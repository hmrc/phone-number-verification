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
import play.api.mvc.Results.{BadGateway, BadRequest, InternalServerError, Ok, ServiceUnavailable, TooManyRequests}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes.{
  EXTERNAL_API_FAIL,
  EXTERNAL_SERVICE_FAIL,
  MESSAGE_THROTTLED_OUT,
  PASSCODE_PERSISTING_FAIL,
  PASSCODE_VERIFY_FAIL,
  VERIFICATION_ERROR
}
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Message.{
  EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE,
  INVALID_TELEPHONE_NUMBER,
  PASSCODE_STORED_TIME_ELAPSED,
  SERVER_CURRENTLY_UNAVAILABLE,
  SERVER_EXPERIENCED_AN_ISSUE
}
import uk.gov.hmrc.cipphonenumberverification.models.api.StatusCode.{INDETERMINATE, NOT_VERIFIED, VERIFIED}
import uk.gov.hmrc.cipphonenumberverification.models.api._
import uk.gov.hmrc.cipphonenumberverification.models.domain.audit.AuditType._
import uk.gov.hmrc.cipphonenumberverification.models.domain.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndPasscode
import uk.gov.hmrc.cipphonenumberverification.models.{api, PhoneNumberPasscodeData}
import uk.gov.hmrc.cipphonenumberverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerifyService @Inject() (passcodeGenerator: PasscodeGenerator,
                               auditService: AuditService,
                               passcodeService: PasscodeService,
                               validateService: ValidateService,
                               userNotificationsConnector: UserNotificationsConnector,
                               metricsService: MetricsService,
                               dateTimeUtils: DateTimeUtils,
                               config: AppConfig
)(implicit val executionContext: ExecutionContext)
    extends Logging {

  def verifyPhoneNumber(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumber.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processPhoneNumber(validatedPhoneNumber)
      case Left(error) =>
        metricsService.recordMetric("CIP-Validation-HTTP-Failure")
        metricsService.recordMetric(error.toString.trim.dropRight(1))
        logger.error(error.message)
        Future.successful(BadRequest(Json.toJson(ErrorResponse(Codes.VALIDATION_ERROR.id, INVALID_TELEPHONE_NUMBER))))
    }

  def verifyPasscode(phoneNumberAndPasscode: PhoneNumberAndPasscode)(implicit hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumberAndPasscode.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processValidPasscode(validatedPhoneNumber, phoneNumberAndPasscode.passcode)
      case Left(error) =>
        metricsService.recordMetric("CIP-Validation-HTTP-Failure")
        metricsService.recordMetric(error.toString.trim.dropRight(1))
        logger.error(error.message)
        Future.successful(BadRequest(Json.toJson(ErrorResponse(Codes.VALIDATION_ERROR.id, INVALID_TELEPHONE_NUMBER))))
    }

  def processPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)(implicit hc: HeaderCarrier): Future[Result] =
    isPhoneTypeValid(validatedPhoneNumber) match {
      case true => processValidPhoneNumber(validatedPhoneNumber)
      case _ =>
        Future(Ok(Json.toJson(Indeterminate(INDETERMINATE, "Only mobile numbers can be verified"))))
    }

  private def isPhoneTypeValid(validatedPhoneNumber: ValidatedPhoneNumber): Boolean =
    validatedPhoneNumber.phoneNumberType match {
      case "Mobile" => true
      case _        => false
    }

  private def processValidPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)(implicit hc: HeaderCarrier): Future[Result] = {
    val passcode   = passcodeGenerator.passcodeGenerator()
    val now        = dateTimeUtils.getCurrentDateTime()
    val dataToSave = new PhoneNumberPasscodeData(validatedPhoneNumber.phoneNumber, passcode, now)
    auditService.sendExplicitAuditEvent(PhoneNumberVerificationRequest, VerificationRequestAuditEvent(dataToSave.phoneNumber, passcode))

    passcodeService.persistPasscode(dataToSave) transformWith {
      case Success(savedPhoneNumberPasscodeData) => sendPasscode(savedPhoneNumberPasscodeData)
      case Failure(err) =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed - ${err.getMessage}")
        Future.successful(InternalServerError(Json.toJson(ErrorResponse(PASSCODE_PERSISTING_FAIL.id, SERVER_EXPERIENCED_AN_ISSUE))))
    }
  }

  private def sendPasscode(data: PhoneNumberPasscodeData)(implicit hc: HeaderCarrier) =
    userNotificationsConnector.sendPasscode(data) map {
      case Left(error) =>
        error.statusCode match {
          case INTERNAL_SERVER_ERROR =>
            metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
            logger.error(error.getMessage)
            BadGateway(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_FAIL.id, SERVER_CURRENTLY_UNAVAILABLE)))
          case BAD_REQUEST | FORBIDDEN =>
            metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
            logger.error(error.getMessage)
            ServiceUnavailable(Json.toJson(ErrorResponse(EXTERNAL_API_FAIL.id, EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
          case TOO_MANY_REQUESTS =>
            metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
            logger.error(error.getMessage)
            TooManyRequests(Json.toJson(api.ErrorResponse(MESSAGE_THROTTLED_OUT.id, "The request for the API is throttled as you have exceeded your quota")))
          case _ =>
            metricsService.recordMetric(s"UpstreamErrorResponse.${error.statusCode}")
            logger.error(error.getMessage)
            Result.apply(ResponseHeader(error.statusCode), HttpEntity.NoEntity)
        }
      case Right(response) if response.status == 200 =>
        metricsService.recordMetric("UserNotifications_success")
        logger.info(response.body)
        Ok(response.body)
      //.withHeaders(("Location", s"/notifications/${response.json.as[GovUkNotificationId].id}"))
    } recover {
      case err =>
        logger.error(err.getMessage)
        metricsService.recordMetric(err.toString.trim.dropRight(1))
        metricsService.recordMetric("UserNotifications_failure")
        ServiceUnavailable(Json.toJson(api.ErrorResponse(EXTERNAL_SERVICE_FAIL.id, "Server currently unavailable")))
    }

  def processValidPasscode(validatedPhoneNumber: ValidatedPhoneNumber, passcodeToCheck: String)(implicit hc: HeaderCarrier): Future[Result] =
    (for {
      maybePhoneNumberAndPasscodeData <- passcodeService.retrievePasscode(validatedPhoneNumber.phoneNumber)
      result                          <- processPasscode(PhoneNumberAndPasscode(validatedPhoneNumber.phoneNumber, passcodeToCheck), maybePhoneNumberAndPasscodeData)
    } yield result).recover {
      case err =>
        metricsService.recordMetric("mongo_cache_failure")
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(ErrorResponse(PASSCODE_VERIFY_FAIL.id, SERVER_EXPERIENCED_AN_ISSUE)))
    }

  private def processPasscode(enteredPhoneNumberAndPasscode: PhoneNumberAndPasscode, maybePhoneNumberAndPasscode: Option[PhoneNumberPasscodeData])(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    maybePhoneNumberAndPasscode match {
      case Some(storedPhoneNumberAndPasscode) =>
//        checkIfPasscodeIsStillAllowedToBeUsed(enteredPhoneNumberAndPasscode, storedPhoneNumberAndPasscode, System.currentTimeMillis())
        checkIfPasscodeMatches(enteredPhoneNumberAndPasscode, storedPhoneNumberAndPasscode)
      case _ =>
        auditService.sendExplicitAuditEvent(
          PhoneNumberVerificationCheck,
          VerificationCheckAuditEvent(enteredPhoneNumberAndPasscode.phoneNumber, enteredPhoneNumberAndPasscode.passcode, NOT_VERIFIED)
        )
        Future.successful(Ok(Json.toJson(ErrorResponse(VERIFICATION_ERROR.id, PASSCODE_STORED_TIME_ELAPSED))))
    }

//  private def checkIfPasscodeIsStillAllowedToBeUsed(enteredPhoneNumberAndpasscode: PhoneNumberAndPasscode,
//                                                    foundPhoneNumberPasscodeData: PhoneNumberPasscodeData,
//                                                    now: Long
//  )(implicit hc: HeaderCarrier): Future[Result] =
//    hasPasscodeExpired(foundPhoneNumberPasscodeData: PhoneNumberPasscodeData, now) match {
//      case true =>
//        auditService.sendExplicitAuditEvent(
//          PhoneNumberVerificationCheck,
//          VerificationCheckAuditEvent(enteredPhoneNumberAndpasscode.phoneNumber, enteredPhoneNumberAndpasscode.passcode, NOT_VERIFIED)
//        )
//        Future.successful(Ok(Json.toJson(ErrorResponse(VERIFICATION_ERROR.id, PASSCODE_ALLOWED_TIME_ELAPSED))))
//      case false => checkIfPasscodeMatches(enteredPhoneNumberAndpasscode, foundPhoneNumberPasscodeData)
//    }

//  private def hasPasscodeExpired(foundPhoneNumberPasscodeData: PhoneNumberPasscodeData, currentTime: Long): Boolean = {
//    val elapsedTimeInMilliseconds: Long                    = calculateElapsedTime(foundPhoneNumberPasscodeData.createdAt, currentTime)
//    val allowedTimeGapForPasscodeUsageInMilliseconds: Long = Duration.ofMinutes(passcodeExpiry).toMillis
//    elapsedTimeInMilliseconds > allowedTimeGapForPasscodeUsageInMilliseconds
//  }

  private def checkIfPasscodeMatches(enteredPhoneNumberAndpasscode: PhoneNumberAndPasscode, maybePhoneNumberAndpasscodeData: PhoneNumberPasscodeData)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    if (passcodeMatches(enteredPhoneNumberAndpasscode.passcode, maybePhoneNumberAndpasscodeData.passcode)) {
      metricsService.recordMetric("passcode_verification_success")
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndpasscode.phoneNumber, enteredPhoneNumberAndpasscode.passcode, VERIFIED)
      )
      Future.successful(Ok(Json.toJson(Verified(VERIFIED))))
    } else {
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndpasscode.phoneNumber, enteredPhoneNumberAndpasscode.passcode, NOT_VERIFIED)
      )
      Future.successful(Ok(Json.toJson(Verified(NOT_VERIFIED))))
    }

  private def passcodeMatches(enteredPasscode: String, storedPasscode: String): Boolean =
    enteredPasscode.equals(storedPasscode)

  def calculateElapsedTime(timeA: Long, timeB: Long): Long =
    timeB - timeA

}
