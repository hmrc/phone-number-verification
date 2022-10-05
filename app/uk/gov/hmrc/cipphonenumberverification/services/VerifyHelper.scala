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
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.api.StatusMessage._
import uk.gov.hmrc.cipphonenumberverification.models.api.{ErrorResponse, Indeterminate, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.models.domain.audit.AuditType._
import uk.gov.hmrc.cipphonenumberverification.models.domain.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndOtp
import uk.gov.hmrc.cipphonenumberverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipphonenumberverification.models.http.validation.ValidatedPhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models._
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes._
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Message._
import uk.gov.hmrc.cipphonenumberverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx, is5xx}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.Duration
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class VerifyHelper @Inject()(otpService: OtpService,
                                      auditService: AuditService,
                                      passcodeService: PasscodeService,
                                      govUkConnector: GovUkConnector,
                                      metricsService: MetricsService,
                                      dateTimeUtils: DateTimeUtils,
                                      config: AppConfig)
                                     (implicit ec: ExecutionContext) extends Logging {

  private val passcodeExpiry = config.passcodeExpiry

  protected def processResponse(res: HttpResponse)(implicit hc: HeaderCarrier): Future[Result] = res match {
    case _ if is2xx(res.status) => processPhoneNumber(res.json.as[ValidatedPhoneNumber])
    case _ if is4xx(res.status) => Future(BadRequest(res.json))
    case _ if is5xx(res.status) => Future(BadGateway(
      Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, SERVER_CURRENTLY_UNAVAILABLE))
    ))
  }

  protected def processResponseForOtp(res: HttpResponse, phoneNumberAndOtp: PhoneNumberAndOtp)
                                     (implicit hc: HeaderCarrier): Future[Result] = res match {
    case _ if is2xx(res.status) => processValidOtp(res.json.as[ValidatedPhoneNumber], phoneNumberAndOtp.otp)
    case _ if is4xx(res.status) => Future(BadRequest(res.json))
    case _ if is5xx(res.status) => Future(BadGateway(
      Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, SERVER_CURRENTLY_UNAVAILABLE))
    ))
  }

  private def processPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)(implicit hc: HeaderCarrier): Future[Result] = {
    isPhoneTypeValid(validatedPhoneNumber) match {
      case true => processValidPhoneNumber(validatedPhoneNumber)
      case _ => Future(Ok(Json.toJson(Indeterminate(INDETERMINATE, "Only mobile numbers can be verified"))))
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
    val now = dateTimeUtils.getCurrentDateTime()
    val dataToSave = new PhoneNumberPasscodeData(validatedPhoneNumber.phoneNumber, otp, now)
    auditService.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
      VerificationRequestAuditEvent(dataToSave.phoneNumber, otp))

    passcodeService.persistPasscode(dataToSave) transformWith {
      case Success(savedPhoneNumberPasscodeData) => sendPasscode(savedPhoneNumberPasscodeData)
      case Failure(err) =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed - ${err.getMessage}")
        Future.successful(InternalServerError(Json.toJson(ErrorResponse(PASSCODE_PERSISTING_FAIL, SERVER_EXPERIENCED_AN_ISSUE))))
    }
  }

  private def sendPasscode(data: PhoneNumberPasscodeData)
                          (implicit hc: HeaderCarrier) = govUkConnector.sendPasscode(data) map {
    case Left(error) => error.statusCode match {
      case INTERNAL_SERVER_ERROR =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        BadGateway(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, SERVER_CURRENTLY_UNAVAILABLE)))
      case BAD_REQUEST | FORBIDDEN =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        ServiceUnavailable(Json.toJson(ErrorResponse(EXTERNAL_API_FAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
      case TOO_MANY_REQUESTS =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        TooManyRequests(Json.toJson(api.ErrorResponse(MESSAGE_THROTTLED_OUT, "The request for the API is throttled as you have exceeded your quota")))
      case _ =>
        metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
        logger.error(error.getMessage)
        Result.apply(ResponseHeader(error.statusCode), HttpEntity.NoEntity)
    }
    case Right(response) if response.status == 201 =>
      metricsService.recordMetric("gov-notify_call_success")
      Accepted.withHeaders(("Location", s"/notifications/${response.json.as[GovUkNotificationId].id}"))
  } recover {
    case err =>
      logger.error(err.getMessage)
      metricsService.recordMetric(err.toString.trim.dropRight(1))
      metricsService.recordMetric("gov-notify_connection_failure")
      ServiceUnavailable(Json.toJson(api.ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable")))
  }

  private def processValidOtp(validatedPhoneNumber: ValidatedPhoneNumber, otp: String)
                             (implicit hc: HeaderCarrier) = {
    (for {
      maybePhoneNumberAndOtpData <- passcodeService.retrievePasscode(validatedPhoneNumber.phoneNumber)
      result <- processPasscode(PhoneNumberAndOtp(validatedPhoneNumber.phoneNumber, otp), maybePhoneNumberAndOtpData)
    } yield result).recover {
      case err =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(ErrorResponse(PASSCODE_VERIFY_FAIL, SERVER_EXPERIENCED_AN_ISSUE)))
    }
  }

  private def processPasscode(enteredPhoneNumberAndOtp: PhoneNumberAndOtp,
                              maybePhoneNumberAndOtp: Option[PhoneNumberPasscodeData])(implicit hc: HeaderCarrier): Future[Result] =
    maybePhoneNumberAndOtp match {
      case Some(storedPhoneNumberAndOtp) => checkIfPasscodeIsStillAllowedToBeUsed(enteredPhoneNumberAndOtp, storedPhoneNumberAndOtp, System.currentTimeMillis())
      case _ => auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, NOT_VERIFIED))
        Future.successful(Ok(Json.toJson(ErrorResponse(VERIFICATION_ERROR, PASSCODE_STORED_TIME_ELAPSED))))
    }

  private def checkIfPasscodeIsStillAllowedToBeUsed(enteredPhoneNumberAndOtp: PhoneNumberAndOtp, foundPhoneNumberPasscodeData: PhoneNumberPasscodeData, now: Long)(implicit hc: HeaderCarrier): Future[Result] = {
    hasPasscodeExpired(foundPhoneNumberPasscodeData: PhoneNumberPasscodeData, now) match {
      case true =>
        auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
          VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, NOT_VERIFIED))
        Future.successful(Ok(Json.toJson(ErrorResponse(VERIFICATION_ERROR, PASSCODE_ALLOWED_TIME_ELAPSED))))
      case false => checkIfPasscodeMatches(enteredPhoneNumberAndOtp, foundPhoneNumberPasscodeData)
    }
  }

  private def hasPasscodeExpired(foundPhoneNumberPasscodeData: PhoneNumberPasscodeData, currentTime: Long): Boolean = {
    val elapsedTimeInMilliseconds: Long = calculateElapsedTime(foundPhoneNumberPasscodeData.createdAt, currentTime)
    val allowedTimeGapForPasscodeUsageInMilliseconds: Long = Duration.ofMinutes(passcodeExpiry).toMillis
    elapsedTimeInMilliseconds > allowedTimeGapForPasscodeUsageInMilliseconds
  }

  private def checkIfPasscodeMatches(enteredPhoneNumberAndOtp: PhoneNumberAndOtp,
                                     maybePhoneNumberAndOtpData: PhoneNumberPasscodeData)(implicit hc: HeaderCarrier): Future[Result] = {
    passcodeMatches(enteredPhoneNumberAndOtp.otp, maybePhoneNumberAndOtpData.otp) match {
      case true =>
        metricsService.recordMetric("otp_verification_success")
        auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
          VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, VERIFIED))
        Future.successful(Ok(Json.toJson(VerificationStatus(VERIFIED))))

      case false => auditService.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, NOT_VERIFIED))
        Future.successful(Ok(Json.toJson(VerificationStatus(NOT_VERIFIED))))
    }
  }

  private def passcodeMatches(enteredPasscode: String, storedPasscode: String): Boolean = {
    enteredPasscode.equals(storedPasscode)
  }

  def calculateElapsedTime(timeA: Long, timeB: Long): Long = {
    timeB - timeA
  }

}
