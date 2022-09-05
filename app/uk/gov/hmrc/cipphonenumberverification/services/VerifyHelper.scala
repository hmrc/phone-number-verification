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
import play.api.http.Status.SERVICE_UNAVAILABLE
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{Accepted, BadGateway, BadRequest, InternalServerError, Ok}
import uk.gov.hmrc.cipphonenumberverification.audit.{AuditType, VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.models.ErrorResponse.Codes.{EXTERNAL_SERVICE_FAIL, PASSCODE_PERSISTING_FAIL, PASSCODE_VERIFY_FAIL}
import uk.gov.hmrc.cipphonenumberverification.models._
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx, is5xx}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class VerifyHelper @Inject()(otpService: OtpService, auditService: AuditService,
                                      passcodeService: PasscodeService, govUkConnector: GovUkConnector)
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
    auditService.sendExplicitAuditEvent(AuditType.PHONE_NUMBER_VERIFICATION_REQUEST.toString,
      VerificationRequestAuditEvent(phoneNumberAndOtp.phoneNumber, otp))

    passcodeService.persistPasscode(phoneNumberAndOtp) transformWith {
      case Success(phoneNumberAndOtp) => sendPasscode(phoneNumberAndOtp)
      case Failure(err) =>
        logger.error(s"Database operation failed - ${err.getMessage}")
        Future.successful(InternalServerError(Json.toJson(ErrorResponse(PASSCODE_PERSISTING_FAIL, "Server has experienced an issue"))))
    }
  }

  private def sendPasscode(phoneNumberAndOtp: PhoneNumberAndOtp)
                          (implicit hc: HeaderCarrier) = govUkConnector.sendPasscode(phoneNumberAndOtp) map {
    case Left(error) => error.statusCode match {
      case SERVICE_UNAVAILABLE =>
        logger.error(error.getMessage())
        BadGateway(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable")))
      case _ =>
        logger.error(s"Gov Notify failure - to be covered by CAV-163")
        InternalServerError(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "to be covered by CAV-163")))
    }
    case Right(response) if response.status == 201 => Accepted(Json.toJson(NotificationId(response.json.as[GovUkNotificationId].id)))
  } recover {
    case err =>
      logger.error(err.getMessage)
      BadGateway(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL, "Server currently unavailable")))
  }

  private def processValidOtp(validatedPhoneNumber: ValidatedPhoneNumber, otp: String)
                             (implicit hc: HeaderCarrier) = {
    (for {
      maybePhoneNumberAndOtp <- passcodeService.retrievePasscode(validatedPhoneNumber.phoneNumber)
      result <- processPasscode(PhoneNumberAndOtp(validatedPhoneNumber.phoneNumber, otp), maybePhoneNumberAndOtp)
    } yield result).recover {
      case err =>
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(ErrorResponse(PASSCODE_VERIFY_FAIL, "Server has experienced an issue")))
    }
  }

  private def processPasscode(enteredPhoneNumberAndOtp: PhoneNumberAndOtp,
                              maybePhoneNumberAndOtp: Option[PhoneNumberAndOtp])(implicit hc: HeaderCarrier): Future[Result] =
    maybePhoneNumberAndOtp match {
      case Some(storedPhoneNumberAndOtp) => checkIfPasscodeMatches(enteredPhoneNumberAndOtp, storedPhoneNumberAndOtp)
      case _ => auditService.sendExplicitAuditEvent(AuditType.PHONE_NUMBER_VERIFICATION_CHECK.toString,
        VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, "Not verified"))
        Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
    }

  private def checkIfPasscodeMatches(enteredPhoneNumberAndOtp: PhoneNumberAndOtp,
                                     maybePhoneNumberAndOtp: PhoneNumberAndOtp)(implicit hc: HeaderCarrier): Future[Result] = {
    passcodeMatches(enteredPhoneNumberAndOtp.otp, maybePhoneNumberAndOtp.otp) match {
      case true =>
        auditService.sendExplicitAuditEvent(AuditType.PHONE_NUMBER_VERIFICATION_CHECK.toString,
          VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, "Verified"))
        passcodeService.deletePasscode(maybePhoneNumberAndOtp)
          .transformWith {
            case Success(_) => Future.successful(Ok(Json.toJson(VerificationStatus("Verified"))))
            case Failure(exception) =>
              logger.error(s"Database operation failed - ${exception.getMessage}")
              Future.successful(InternalServerError(Json.toJson(ErrorResponse(PASSCODE_VERIFY_FAIL, "Server has experienced an issue"))))
          }

      case false => auditService.sendExplicitAuditEvent(AuditType.PHONE_NUMBER_VERIFICATION_CHECK.toString,
        VerificationCheckAuditEvent(enteredPhoneNumberAndOtp.phoneNumber, enteredPhoneNumberAndOtp.otp, "Not verified"))
        Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
    }
  }

  private def passcodeMatches(enteredPasscode: String, storedPasscode: String): Boolean = {
    enteredPasscode.equals(storedPasscode)
  }
}
