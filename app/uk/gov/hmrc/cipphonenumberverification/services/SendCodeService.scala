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
import play.api.mvc.{Request, ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.models
import uk.gov.hmrc.cipphonenumberverification.models.audit.AuditType._
import uk.gov.hmrc.cipphonenumberverification.models.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberVerificationCodeData, ValidatedPhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.request.{PhoneNumber, PhoneNumberAndVerificationCode}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage
import uk.gov.hmrc.cipphonenumberverification.models.response.VerificationStatus
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SendCodeService @Inject() (verificationCodeGenerator: VerificationCodeGenerator,
                                 auditService: AuditService,
                                 verificationCodeService: VerificationCodeService,
                                 validateService: ValidateService,
                                 userNotificationsConnector: UserNotificationsConnector,
                                 metricsService: MetricsService
)(implicit val executionContext: ExecutionContext)
    extends Logging {
  import ValidatedPhoneNumber.Implicits._
  import VerificationCheckAuditEvent.Implicits._
  import VerificationRequestAuditEvent.Implicits._

  def sendCode(phoneNumber: PhoneNumber)(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumber.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processPhoneNumber(validatedPhoneNumber)
      case Left(error) =>
        metricsService.recordVerificationStatus(error)
        logger.error(error.message.toString)
        Future.successful(BadRequest(Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER))))
    }

  def verifyVerificationCode(
    phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode
  )(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumberAndVerificationCode.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processValidVerificationCode(validatedPhoneNumber, phoneNumberAndVerificationCode.verificationCode)
      case Left(error) =>
        metricsService.recordVerificationStatus(error)
        logger.error(error.message.toString)
        Future.successful(BadRequest(Json.toJson(VerificationStatus(StatusCode.CODE_VERIFY_FAILURE, StatusMessage.CODE_NOT_RECOGNISED))))
    }

  private def processPhoneNumber(validatedPhoneNumber: ValidatedPhoneNumber)(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] =
    if (validatedPhoneNumber.isMobile) {
      val verificationCode = verificationCodeGenerator.generate()
      val dataToSave       = new PhoneNumberVerificationCodeData(validatedPhoneNumber.phoneNumber, verificationCode)
      auditService.sendExplicitAuditEvent(PhoneNumberVerificationRequest, VerificationRequestAuditEvent(dataToSave.phoneNumber, verificationCode))

      verificationCodeService.persistVerificationCode(dataToSave) transformWith {
        case Success(savedPhoneNumberverificationCodeData) => sendVerificationCode(savedPhoneNumberverificationCodeData)
        case Failure(err) =>
          metricsService.recordMongoCacheFailure()
          logger.error(s"Database operation failed - ${err.getMessage}")
          Future.successful(
            InternalServerError(Json.toJson(VerificationStatus(StatusCode.CODE_PERSISTING_FAIL, StatusMessage.SERVER_EXPERIENCED_AN_ISSUE)))
          )
      }
    } else {
      Future(Ok(Json.toJson(new VerificationStatus(StatusCode.INDETERMINATE, StatusMessage.ONLY_MOBILES_VERIFIABLE))))
    }

  private def sendVerificationCode(data: PhoneNumberVerificationCodeData)(implicit req: Request[JsValue], hc: HeaderCarrier) =
    userNotificationsConnector.sendVerificationCode(data) map {
      case Left(error) =>
        error.statusCode match {
          case INTERNAL_SERVER_ERROR =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            BadGateway(Json.toJson(VerificationStatus(StatusCode.EXTERNAL_SERVICE_FAIL, StatusMessage.SERVER_CURRENTLY_UNAVAILABLE)))
          case BAD_REQUEST | FORBIDDEN =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            ServiceUnavailable(Json.toJson(VerificationStatus(StatusCode.EXTERNAL_API_FAIL, StatusMessage.EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE)))
          case TOO_MANY_REQUESTS =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            TooManyRequests(Json.toJson(VerificationStatus(StatusCode.MESSAGE_THROTTLED_OUT, StatusMessage.SERVICE_THROTTLED_ERROR)))
          case _ =>
            metricsService.recordUpstreamError(error)
            logger.error(error.getMessage)
            Result.apply(ResponseHeader(error.statusCode), HttpEntity.NoEntity)
        }
      case Right(response) if response.status == 200 =>
        metricsService.recordSendNotificationSuccess()
        Ok(Json.toJson[VerificationStatus](VerificationStatus(StatusCode.CODE_SENT, StatusMessage.CODE_SENT)))
    } recover {
      case err =>
        logger.error(err.getMessage, err)
        metricsService.recordError(err)
        metricsService.recordSendNotificationFailure()
        ServiceUnavailable(
          Json.toJson(models.response.VerificationStatus(StatusCode.EXTERNAL_SERVICE_FAIL, StatusMessage.EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE))
        )
    }

  def processValidVerificationCode(validatedPhoneNumber: ValidatedPhoneNumber, verificationCodeToCheck: String)(implicit
    req: Request[JsValue],
    hc: HeaderCarrier
  ): Future[Result] =
    (for {
      maybePhoneNumberAndverificationCodeData <- verificationCodeService.retrieveVerificationCode(validatedPhoneNumber.phoneNumber)
      result <- processVerificationCode(PhoneNumberAndVerificationCode(validatedPhoneNumber.phoneNumber, verificationCodeToCheck),
                                        maybePhoneNumberAndverificationCodeData
      )
    } yield result).recover {
      case err =>
        metricsService.recordMongoCacheFailure()
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(VerificationStatus(StatusCode.CODE_VERIFY_FAILURE, StatusMessage.SERVER_EXPERIENCED_AN_ISSUE)))
    }

  private def processVerificationCode(enteredPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode,
                                      maybePhoneNumberAndVerificationCode: Option[PhoneNumberVerificationCodeData]
  )(implicit
    req: Request[JsValue],
    hc: HeaderCarrier
  ): Future[Result] =
    maybePhoneNumberAndVerificationCode match {
      case Some(storedPhoneNumberAndVerificationCode) =>
        checkIfVerificationCodeMatches(enteredPhoneNumberAndVerificationCode, storedPhoneNumberAndVerificationCode)
      case _ =>
        auditService.sendExplicitAuditEvent(
          PhoneNumberVerificationCheck,
          VerificationCheckAuditEvent(enteredPhoneNumberAndVerificationCode.phoneNumber,
                                      enteredPhoneNumberAndVerificationCode.verificationCode,
                                      StatusCode.CODE_NOT_SENT
          )
        )
        Future.successful(Ok(Json.toJson(VerificationStatus(StatusCode.CODE_SEND_ERROR, StatusMessage.CODE_NOT_RECOGNISED))))
    }

  private def checkIfVerificationCodeMatches(enteredPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode,
                                             maybePhoneNumberAndverificationCodeData: PhoneNumberVerificationCodeData
  )(implicit
    req: Request[JsValue],
    hc: HeaderCarrier
  ): Future[Result] =
    if (enteredPhoneNumberAndVerificationCode.verificationCode == maybePhoneNumberAndverificationCodeData.verificationCode) {
      metricsService.recordCodeVerified()
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndVerificationCode.phoneNumber,
                                    enteredPhoneNumberAndVerificationCode.verificationCode,
                                    StatusCode.CODE_SENT
        )
      )
      Future.successful(Ok(Json.toJson(new VerificationStatus(StatusCode.CODE_VERIFIED, StatusMessage.CODE_VERIFIED))))
    } else {
      metricsService.recordCodeNotVerified()
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationCheck,
        VerificationCheckAuditEvent(enteredPhoneNumberAndVerificationCode.phoneNumber,
                                    enteredPhoneNumberAndVerificationCode.verificationCode,
                                    StatusCode.CODE_NOT_SENT
        )
      )
      Future.successful(NotFound(Json.toJson(new VerificationStatus(StatusCode.CODE_VERIFY_FAILURE, StatusMessage.CODE_NOT_RECOGNISED))))
    }
}
