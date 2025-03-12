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

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
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
import uk.gov.hmrc.cipphonenumberverification.services.TestSendCodeService.testVerificationCode
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait SendCodeService {
  def sendCode(phoneNumber: PhoneNumber)(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result]
  def verifyVerificationCode(phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode)(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result]
}

class LiveSendCodeService @Inject() (verificationCodeGenerator: VerificationCodeGenerator,
                                     auditService: AuditService,
                                     verificationCodeService: VerificationCodeService,
                                     validateService: ValidateService,
                                     userNotificationsConnector: UserNotificationsConnector,
                                     metricsService: MetricsService
)(implicit val executionContext: ExecutionContext)
    extends SendCodeService
    with Logging {
  import ValidatedPhoneNumber.Implicits._
  import VerificationCheckAuditEvent.Implicits._
  import VerificationRequestAuditEvent.Implicits._

  override def sendCode(phoneNumber: PhoneNumber)(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumber.phoneNumber) match {
      case Right(validatedPhoneNumber) =>
        processPhoneNumber(validatedPhoneNumber)
      case Left(error) =>
        metricsService.recordVerificationStatus(error)
        logger.error(error.message.toString)
        Future.successful(BadRequest(Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER))))
    }

  override def verifyVerificationCode(
    phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode
  )(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] =
    validateService.validate(phoneNumberAndVerificationCode.phoneNumber) match {
      case Right(validatedPhoneNumber) if validatedPhoneNumber.phoneNumberType == PhoneNumberType.MOBILE =>
        processValidVerificationCode(validatedPhoneNumber, phoneNumberAndVerificationCode.verificationCode)
      case Right(validatedPhoneNumber) =>
        logger.error("Phone number must be a mobile phone number")
        Future.successful(BadRequest(Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.ONLY_MOBILES_VERIFIABLE))))
      case Left(error) =>
        metricsService.recordVerificationStatus(error)
        logger.error(error.message.toString)
        Future.successful(BadRequest(Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE))))
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
      Future(BadRequest(Json.toJson(new VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.ONLY_MOBILES_VERIFIABLE))))
    }

  private def sendVerificationCode(data: PhoneNumberVerificationCodeData)(implicit hc: HeaderCarrier) =
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
          PhoneNumberVerificationResult,
          VerificationCheckAuditEvent(enteredPhoneNumberAndVerificationCode.phoneNumber,
                                      enteredPhoneNumberAndVerificationCode.verificationCode,
                                      StatusCode.CODE_VERIFY_FAILURE
          )
        )
        Future.successful(Ok(Json.toJson(VerificationStatus(StatusCode.CODE_VERIFY_FAILURE, StatusMessage.CODE_NOT_RECOGNISED))))
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
        PhoneNumberVerificationResult,
        VerificationCheckAuditEvent(enteredPhoneNumberAndVerificationCode.phoneNumber,
                                    enteredPhoneNumberAndVerificationCode.verificationCode,
                                    StatusCode.CODE_VERIFIED
        )
      )
      Future.successful(Ok(Json.toJson(new VerificationStatus(StatusCode.CODE_VERIFIED, StatusMessage.CODE_VERIFIED))))
    } else {
      metricsService.recordCodeNotVerified()
      auditService.sendExplicitAuditEvent(
        PhoneNumberVerificationResult,
        VerificationCheckAuditEvent(enteredPhoneNumberAndVerificationCode.phoneNumber,
                                    enteredPhoneNumberAndVerificationCode.verificationCode,
                                    StatusCode.CODE_VERIFY_FAILURE
        )
      )
      Future.successful(NotFound(Json.toJson(new VerificationStatus(StatusCode.CODE_VERIFY_FAILURE, StatusMessage.CODE_NOT_RECOGNISED))))
    }
}

class TestSendCodeService @Inject() (validateService: ValidateService)(implicit val executionContext: ExecutionContext) extends SendCodeService with Logging {

  override def sendCode(phoneNumber: PhoneNumber)(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] = {
    import StatusCode._
    import StatusMessage._

    validateService.validate(phoneNumber.phoneNumber) match {
      case Left(_) =>
        Future.successful(BadRequest(Json.toJson(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))))
      case Right(value) =>
        val (telNum, telNumType) = (value.phoneNumber, value.phoneNumberType)
        Future.successful(telNum match {
          case TestSendCodeService.verifiablePhoneNumber =>
            Ok(Json.toJson(VerificationStatus(StatusCode.CODE_SENT, StatusMessage.CODE_SENT)))
          case TestSendCodeService.nonMobilePhoneNumber =>
            BadRequest(Json.toJson(VerificationStatus(VALIDATION_ERROR, ONLY_MOBILES_VERIFIABLE)))
          case _ =>
            BadRequest(Json.toJson(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER)))
        })
    }
  }

  override def verifyVerificationCode(
    phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode
  )(implicit req: Request[JsValue], hc: HeaderCarrier): Future[Result] = {
    import StatusCode._
    import StatusMessage._

    validateService.validate(phoneNumberAndVerificationCode.phoneNumber) match {
      case Left(_) =>
        Future.successful(BadRequest(Json.toJson(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))))
      case Right(value) =>
        val (telNum, telNumType) = (value.phoneNumber, value.phoneNumberType)
        Future.successful(telNum match {
          case TestSendCodeService.verifiablePhoneNumber if phoneNumberAndVerificationCode.verificationCode == testVerificationCode =>
            Ok(Json.toJson(VerificationStatus(StatusCode.CODE_VERIFIED, StatusMessage.CODE_VERIFIED)))
          case TestSendCodeService.nonVerifiableCodePhoneNumber if phoneNumberAndVerificationCode.verificationCode == testVerificationCode =>
            NotFound(Json.toJson(new VerificationStatus(CODE_VERIFY_FAILURE, CODE_NOT_RECOGNISED)))
          case TestSendCodeService.nonVerifiablePhoneNumber =>
            NotFound(Json.toJson(new VerificationStatus(CODE_VERIFY_FAILURE, CODE_NOT_RECOGNISED)))
          case _ =>
            BadRequest(Json.toJson(VerificationStatus(CODE_VERIFY_FAILURE, INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE)))
        })
    }
  }
}

object TestSendCodeService {
  val verifiablePhoneNumber        = "+447966123123"
  val invalidPhoneNumber           = "12345"
  val nonMobilePhoneNumber         = "+441494123124"
  val nonVerifiablePhoneNumber     = "+447966123124"
  val nonVerifiableCodePhoneNumber = "+447966555666"
  val testVerificationCode         = "ABCDEF"
}
