/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cipphonenumberverification.services

import play.api.Logging
import play.api.http.HttpEntity
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, TOO_MANY_REQUESTS}
import play.api.libs.json.Json
import play.api.mvc.Results.{Accepted, BadGateway, BadRequest, InternalServerError, Ok, ServiceUnavailable, TooManyRequests}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.audit.AuditType.{PhoneNumberVerificationCheck, PhoneNumberVerificationRequest}
import uk.gov.hmrc.cipphonenumberverification.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.ErrorResponse.Codes._
import uk.gov.hmrc.cipphonenumberverification.models._
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx, is5xx}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class VerifyHelper @Inject()(otpService: OtpService,
                                      auditService: AuditService,
                                      passcodeService: PasscodeService,
                                      govUkConnector: GovUkConnector,
                                      metricsService: MetricsService)
                                     (implicit ec: ExecutionContext) extends Logging {

  protected def processResponse(res: HttpResponse)(implicit hc: HeaderCarrier): Future[Result] = res match {
    case _ if is2xx(res.status) => processPhoneNumber(res.json.as[ValidatedPhoneNumber])
    case _ if is4xx(res.status) => Future(BadRequest(res.json))
    case _ if is5xx(res.status) => Future(BadGateway(
      Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable"))
    ))
  }

  protected def processResponseForOtp(res: HttpResponse, phoneNumberAndOtp: PhoneNumberAndOtp)
                                     (implicit hc: HeaderCarrier): Future[Result] = res match {
    case _ if is2xx(res.status) => processValidOtp(res.json.as[ValidatedPhoneNumber], phoneNumberAndOtp.otp)
    case _ if is4xx(res.status) => Future(BadRequest(res.json))
    case _ if is5xx(res.status) => Future(BadGateway(
      Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable"))
    ))
  }

  private def processPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)(implicit hc: HeaderCarrier): Future[Result] = {
    isPhoneTypeValid(validatedPhoneNumber) match {
      case true => processValidPhoneNumber(validatedPhoneNumber)
      case _ => Future(Ok(Json.toJson(Indeterminate("Indeterminate", "Only mobile numbers can be verified"))))
    }
  }

  private def isPhoneTypeValid(validatedPhoneNumber: ValidatedPhoneNumber): Boolean = {
    validatedPhoneNumber.phoneNumberType match {
      case "Mobile" => true
      case _ => false
    }
  }

  private def processValidPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)
                                     (implicit hc: HeaderCarrier): Future[Result] = {
    val otp = otpService.otpGenerator()
    val phoneNumberAndOtp = PhoneNumberAndOtp(validatedPhoneNumber.phoneNumber, otp)
    auditService.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
      VerificationRequestAuditEvent(phoneNumberAndOtp.phoneNumber, otp))

    passcodeService.persistPasscode(phoneNumberAndOtp) transformWith {
      case Success(phoneNumberAndOtp) => sendPasscode(phoneNumberAndOtp)
      case Failure(err) =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed - ${err.getMessage}")
        Future.successful(InternalServerError(Json.toJson(ErrorResponse(PASSCODE_PERSISTING_FAIL, "Server has experienced an issue"))))
    }
  }

  private def sendPasscode(phoneNumberAndOtp: PhoneNumberAndOtp)
                          (implicit hc: HeaderCarrier) = govUkConnector.sendPasscode(phoneNumberAndOtp) map {
    case Left(error) => error.statusCode match {
      case INTERNAL_SERVER_ERROR =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        BadGateway(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable")))
      case BAD_REQUEST | FORBIDDEN =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        ServiceUnavailable(Json.toJson(ErrorResponse(EXTERNAL_API_FAIL, "External server currently unavailable")))
      case TOO_MANY_REQUESTS =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        TooManyRequests(Json.toJson(ErrorResponse(MESSAGE_THROTTLED_OUT, "The request for the API is throttled as you have exceeded your quota")))
      case _ =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        Result.apply(ResponseHeader(error.statusCode), HttpEntity.NoEntity)
    }
    case Right(response) if response.status == 201 =>
      metricsService.recordMetric("gov-notify_call_success")
      Accepted(Json.toJson(NotificationId(response.json.as[GovUkNotificationId].id)))
  } recover {
    case err =>
      logger.error(err.getMessage)
      metricsService.recordMetric(err.toString.trim.dropRight(1))
      metricsService.recordMetric("gov-notify_connection_failure")
      ServiceUnavailable(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable")))
  }

  private def processValidOtp(validatedPhoneNumber: ValidatedPhoneNumber, otp: String)
                             (implicit hc: HeaderCarrier) = {
    (for {
      maybePhoneNumberAndOtp <- passcodeService.retrievePasscode(validatedPhoneNumber.phoneNumber)
      result <- processPasscode(PhoneNumberAndOtp(validatedPhoneNumber.phoneNumber, otp), maybePhoneNumberAndOtp)
    } yield result).recover {
      case err =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(ErrorResponse(PASSCODE_VERIFY_FAIL, "Server has experienced an issue")))
    }
  }

  private def processPasscode(enteredPhoneNumberAndOtp: PhoneNumberAndOtp,
                              maybePhoneNumberAndOtp: Option[PhoneNumberAndOtp])(implicit hc: HeaderCarrier): Future[Result] =
    maybePhoneNumberAndOtp match {
      case Some(storedPhoneNumberAndOtp) => checkIfPasscodeMatches(enteredPhoneNumberAndOtp, storedPhoneNumberAndOtp)
      case _ => auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, "Not verified"))
        Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
    }

  private def checkIfPasscodeMatches(enteredPhoneNumberAndOtp: PhoneNumberAndOtp,
                                     maybePhoneNumberAndOtp: PhoneNumberAndOtp)(implicit hc: HeaderCarrier): Future[Result] = {
    passcodeMatches(enteredPhoneNumberAndOtp.otp, maybePhoneNumberAndOtp.otp) match {
      case true =>
        metricsService.recordMetric("otp_verification_success")
        auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
          VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, "Verified"))
        passcodeService.deletePasscode(maybePhoneNumberAndOtp).transformWith {
          case Success(_) => Future.successful(Ok(Json.toJson(VerificationStatus("Verified"))))
          case Failure(exception) =>
            logger.error(s"Database operation failed - ${exception.getMessage}")
            Future.successful(InternalServerError(Json.toJson(ErrorResponse(PASSCODE_VERIFY_FAIL, "Server has experienced an issue"))))
        }

      case false => auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, "Not verified"))
        Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
    }
  }

  private def passcodeMatches(enteredPasscode: String, storedPasscode: String): Boolean = {
    enteredPasscode.equals(storedPasscode)
  }
}
