/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json._
import play.api.mvc.Results.{BadGateway, BadRequest, InternalServerError, NotFound, Ok, ServiceUnavailable, TooManyRequests}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.models
import uk.gov.hmrc.cipphonenumberverification.models.audit.AuditType._
import uk.gov.hmrc.cipphonenumberverification.models.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberPasscodeData, ValidatedPhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.request.{PhoneNumber, PhoneNumberAndPasscode}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.{
  EXTERNAL_API_FAIL,
  EXTERNAL_SERVICE_FAIL,
  INDETERMINATE,
  MESSAGE_THROTTLED_OUT,
  NOT_VERIFIED,
  PASSCODE_PERSISTING_FAIL,
  PASSCODE_VERIFIED,
  PASSCODE_VERIFY_FAIL,
  VALIDATION_ERROR,
  VERIFICATION_ERROR,
  VERIFIED
}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.{
  EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE,
  INVALID_TELEPHONE_NUMBER,
  ONLY_MOBILES_VERIFIABLE,
  PASSCODE_NOT_RECOGNISED,
  SERVER_CURRENTLY_UNAVAILABLE,
  SERVER_EXPERIENCED_AN_ISSUE,
  SERVICE_THROTTLED_ERROR
}
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusMessage, VerificationStatus}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerifyService @Inject() (passcodeGenerator: PasscodeGenerator,
                               auditService: AuditService,
                               passcodeService: PasscodeService,
                               validateService: ValidateService,
                               userNotificationsConnector: UserNotificationsConnector,
                               metricsService: MetricsService
)(implicit val executionContext: ExecutionContext)
    extends Logging {
  import ValidatedPhoneNumber.Implicits._
  import VerificationCheckAuditEvent.Implicits._
  import VerificationRequestAuditEvent.Implicits._

  def verifyPhoneNumber(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumber.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processPhoneNumber(validatedPhoneNumber)
      case Left(error) =>
        metricsService.recordVerificationStatus(error)
        logger.error(error.message.toString)
        Future.successful(BadRequest(Json.toJson(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))))
    }

  def verifyPasscode(phoneNumberAndPasscode: PhoneNumberAndPasscode)(implicit hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumberAndPasscode.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processValidPasscode(validatedPhoneNumber, phoneNumberAndPasscode.passcode)
      case Left(error) =>
        metricsService.recordVerificationStatus(error)
        logger.error(error.message.toString)
        Future.successful(BadRequest(Json.toJson(VerificationStatus(PASSCODE_VERIFY_FAIL, PASSCODE_NOT_RECOGNISED))))
    }

  private def processPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)(implicit hc: HeaderCarrier): Future[Result] =
    if (validatedPhoneNumber.isMobile) {
      val passcode   = passcodeGenerator.passcodeGenerator()
      val dataToSave = new PhoneNumberPasscodeData(validatedPhoneNumber.phoneNumber, passcode)
      auditService.sendExplicitAuditEvent(PhoneNumberVerificationRequest, VerificationRequestAuditEvent(dataToSave.phoneNumber, passcode))

      passcodeService.persistPasscode(dataToSave) transformWith {
        case Success(savedPhoneNumberPasscodeData) => sendPasscode(savedPhoneNumberPasscodeData)
        case Failure(err) =>
          metricsService.recordMongoCacheFailure()
          logger.error(s"Database operation failed - ${err.getMessage}")
          Future.successful(InternalServerError(Json.toJson(VerificationStatus(PASSCODE_PERSISTING_FAIL, SERVER_EXPERIENCED_AN_ISSUE))))
      }
    } else {
      Future(Ok(Json.toJson(new VerificationStatus(INDETERMINATE, ONLY_MOBILES_VERIFIABLE))))
    }

  private def sendPasscode(data: PhoneNumberPasscodeData)(implicit hc: HeaderCarrier) =
    userNotificationsConnector.sendPasscode(data) map {
      case Left(error) =>
        error.statusCode match {
          case INTERNAL_SERVER_ERROR =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            BadGateway(Json.toJson(VerificationStatus(EXTERNAL_SERVICE_FAIL, SERVER_CURRENTLY_UNAVAILABLE)))
          case BAD_REQUEST | FORBIDDEN =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            ServiceUnavailable(Json.toJson(VerificationStatus(EXTERNAL_API_FAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
          case TOO_MANY_REQUESTS =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            TooManyRequests(Json.toJson(VerificationStatus(MESSAGE_THROTTLED_OUT, SERVICE_THROTTLED_ERROR)))
          case _ =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            Result.apply(ResponseHeader(error.statusCode), HttpEntity.NoEntity)
        }
      case Right(response) if response.status == 200 =>
        metricsService.recordSendNotificationSuccess()
        Ok(Json.toJson[VerificationStatus](VerificationStatus(VERIFIED, StatusMessage.VERIFIED)))
    } recover {
      case err =>
        logger.error(err.getMessage, err)
        metricsService.recordError(err)
        metricsService.recordSendNotificationFailure()
        ServiceUnavailable(Json.toJson(models.response.VerificationStatus(EXTERNAL_SERVICE_FAIL, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
    }

  def processValidPasscode(validatedPhoneNumber: ValidatedPhoneNumber, passcodeToCheck: String)(implicit hc: HeaderCarrier): Future[Result] =
    (for {
      maybePhoneNumberAndPasscodeData <- passcodeService.retrievePasscode(validatedPhoneNumber.phoneNumber)
      result                          <- processPasscode(PhoneNumberAndPasscode(validatedPhoneNumber.phoneNumber, passcodeToCheck), maybePhoneNumberAndPasscodeData)
    } yield result).recover {
      case err =>
        metricsService.recordMongoCacheFailure()
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(VerificationStatus(PASSCODE_VERIFY_FAIL, SERVER_EXPERIENCED_AN_ISSUE)))
    }

  private def processPasscode(enteredPhoneNumberAndPasscode: PhoneNumberAndPasscode, maybePhoneNumberAndPasscode: Option[PhoneNumberPasscodeData])(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    maybePhoneNumberAndPasscode match {
      case Some(storedPhoneNumberAndPasscode) =>
        checkIfPasscodeMatches(enteredPhoneNumberAndPasscode, storedPhoneNumberAndPasscode)
      case _ =>
        auditService.sendExplicitAuditEvent(
          PhoneNumberVerificationCheck,
          VerificationCheckAuditEvent(enteredPhoneNumberAndPasscode.phoneNumber, enteredPhoneNumberAndPasscode.passcode, NOT_VERIFIED)
        )
        Future.successful(Ok(Json.toJson(VerificationStatus(VERIFICATION_ERROR, PASSCODE_NOT_RECOGNISED))))
    }

  private def checkIfPasscodeMatches(enteredPhoneNumberAndpasscode: PhoneNumberAndPasscode, maybePhoneNumberAndpasscodeData: PhoneNumberPasscodeData)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    if (enteredPhoneNumberAndpasscode.passcode == maybePhoneNumberAndpasscodeData.passcode) {
      metricsService.recordPasscodeVerified()
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndpasscode.phoneNumber, enteredPhoneNumberAndpasscode.passcode, VERIFIED)
      )
      Future.successful(Ok(Json.toJson(new VerificationStatus(PASSCODE_VERIFIED, StatusMessage.PASSCODE_VERIFIED))))
    } else {
      metricsService.recordPasscodeNotVerified()
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndpasscode.phoneNumber, enteredPhoneNumberAndpasscode.passcode, NOT_VERIFIED)
      )
      Future.successful(NotFound(Json.toJson(new VerificationStatus(PASSCODE_VERIFY_FAIL, PASSCODE_NOT_RECOGNISED))))
    }
}
