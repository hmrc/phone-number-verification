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
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Headers, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.models.audit.AuditType.{PhoneNumberVerificationRequest, PhoneNumberVerificationResult}
import uk.gov.hmrc.cipphonenumberverification.models.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberVerificationCodeData, ValidatedPhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.request.{PhoneNumber, PhoneNumberAndVerificationCode}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.{
  CODE_PERSISTING_FAIL,
  CODE_SENT,
  CODE_VERIFY_FAILURE,
  EXTERNAL_API_FAIL,
  EXTERNAL_SERVICE_FAIL,
  MESSAGE_THROTTLED_OUT,
  VALIDATION_ERROR
}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.{
  CODE_NOT_RECOGNISED,
  EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE,
  INVALID_TELEPHONE_NUMBER,
  SERVER_CURRENTLY_UNAVAILABLE,
  SERVER_EXPERIENCED_AN_ISSUE,
  SERVICE_THROTTLED_ERROR
}
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage, VerificationStatus}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SendCodeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import VerificationCheckAuditEvent.Implicits._
  import VerificationRequestAuditEvent.Implicits._

  "verify" should {
    "return success if telephone number is valid" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.successful(phoneNumberVerificationCodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendVerificationCode(phoneNumberVerificationCodeDataFromDb)
      )
        .thenReturn(
          Future.successful(
            Right(HttpResponse(OK, Json.toJson("""{"deliveryStatus" : "SUCCESSFUL"}"""), Map(HeaderNames.CONTENT_TYPE -> Seq(MimeTypes.JSON))))
          )
        )

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(VerificationStatus(CODE_SENT, StatusMessage.CODE_SENT))

      // header("Location", result) shouldBe Some("/notifications/test-notification-id")

      // check what is sent to validation service
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(metricsServiceMock, atLeastOnce()).recordSendNotificationSuccess()
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      // check what is sent to the audit service
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      // check what is sent to DAO
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(phoneNumberVerificationCodeDataFromDb)
      // Check what is sent to GovNotify
      verify(userNotificationsConnectorMock, atLeastOnce()).sendVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))(any[HeaderCarrier])
    }

    "return error response if telephone number is invalid" in new SetUp {
      import uk.gov.hmrc.cipphonenumberverification.models.response.VerificationStatus._

      val enteredPhoneNumber: PhoneNumber = PhoneNumber("test")
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Left(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER)))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe BAD_REQUEST
      val errorResponse: VerificationStatus = contentAsJson(result).as[VerificationStatus]
      errorResponse.status shouldBe VALIDATION_ERROR
      errorResponse.message shouldBe INVALID_TELEPHONE_NUMBER
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any[Request[JsValue]], any[HeaderCarrier], any())
      verify(verificationCodeGeneratorMock, never()).generate()
      verify(verificationServiceMock, never()).persistVerificationCode(any())
      verify(verificationServiceMock, never()).retrieveVerificationCode(any())
      verify(userNotificationsConnectorMock, never()).sendVerificationCode(any())(any())
    }

    "return internal server error when datastore exception occurs" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      val phoneNumberVerificationCdeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe CODE_PERSISTING_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      // check what is sent to validation service
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      // check what is sent to the audit service
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      // check what is sent to DAO
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(phoneNumberVerificationCdeDataFromDb)

      // Check NOTHING is sent to GovNotify
      verify(userNotificationsConnectorMock, never()).sendVerificationCode(any())(any())
    }

    "return BadGateway if user notifications returns internal server error" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.successful(phoneNumberVerificationCodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendVerificationCode(any[PhoneNumberVerificationCodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("Server currently unavailable", INTERNAL_SERVER_ERROR))))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe EXTERNAL_SERVICE_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVER_CURRENTLY_UNAVAILABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(meq(PhoneNumberVerificationRequest), meq(expectedAuditEvent))(any[Request[JsValue]],
                                                                                                                                   any[HeaderCarrier],
                                                                                                                                   any()
      )
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))
      verify(userNotificationsConnectorMock, atLeastOnce()).sendVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }

    "return Service unavailable if gov-notify returns BadRequestError" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.successful(phoneNumberVerificationCodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendVerificationCode(any[PhoneNumberVerificationCodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", BAD_REQUEST))))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe EXTERNAL_API_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(phoneNumberVerificationCodeDataFromDb)
      verify(userNotificationsConnectorMock, atLeastOnce()).sendVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }

    "return Service unavailable if gov-notify returns Forbidden error" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.successful(phoneNumberVerificationCodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendVerificationCode(any[PhoneNumberVerificationCodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", FORBIDDEN))))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe EXTERNAL_API_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(phoneNumberVerificationCodeDataFromDb)
      verify(userNotificationsConnectorMock, atLeastOnce()).sendVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }

    "return Too Many Requests if gov-notify returns RateLimitError or TooManyRequestsError" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.successful(phoneNumberVerificationCodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendVerificationCode(any[PhoneNumberVerificationCodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", TOO_MANY_REQUESTS))))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe TOO_MANY_REQUESTS
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe MESSAGE_THROTTLED_OUT
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVICE_THROTTLED_ERROR
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))
      verify(userNotificationsConnectorMock, atLeastOnce()).sendVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))(any[HeaderCarrier])
    }

    "return indeterminate response if phone number is not a mobile" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.FIXED_LINE)))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.VALIDATION_ERROR
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe StatusMessage.ONLY_MOBILES_VERIFIABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any(), any(), any())
      verify(verificationServiceMock, never()).persistVerificationCode(any())
      verify(verificationServiceMock, never()).retrieveVerificationCode(any())
      verify(userNotificationsConnectorMock, never()).sendVerificationCode(any())(any[HeaderCarrier])
    }

    "return response from service if response has not been handled" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                                          = PhoneNumber("test")
      val normalisedPhoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("normalised-phone-number", verificationCode)
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(normalisedPhoneNumberAndVerificationCode.phoneNumber, normalisedPhoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .persistVerificationCode(any[PhoneNumberVerificationCodeData])
      )
        .thenReturn(Future.successful(phoneNumberVerificationCodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendVerificationCode(any[PhoneNumberVerificationCodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("Some random message from external service", CONFLICT))))

      val result: Future[Result] = verifyService.sendCode(enteredPhoneNumber)

      status(result) shouldBe CONFLICT
      contentAsString(result) shouldBe empty
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(verificationCodeGeneratorMock, atLeastOnce()).generate()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalised-phone-number", verificationCode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(verificationServiceMock, atLeastOnce()).persistVerificationCode(phoneNumberVerificationCodeDataFromDb)
      verify(userNotificationsConnectorMock, atLeastOnce()).sendVerificationCode(meq(phoneNumberVerificationCodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }
  }

  "verifyVerificationCode" should {
    "return Verified if verification code matches" in new SetUp {
      val phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("enteredPhoneNumber", "enteredVerificationCode")
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(phoneNumberAndVerificationCode.phoneNumber, phoneNumberAndVerificationCode.verificationCode)
      when(
        validateServiceMock
          .validate(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .retrieveVerificationCode(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Future.successful(Some(phoneNumberVerificationCodeDataFromDb)))

      val result: Future[Result] = verifyService.verifyVerificationCode(phoneNumberAndVerificationCode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.CODE_VERIFIED
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent: VerificationCheckAuditEvent =
        VerificationCheckAuditEvent("enteredPhoneNumber", "enteredVerificationCode", StatusCode.CODE_VERIFIED)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationResult, expectedVerificationCheckAuditEvent)
    }

    "return verification error and enter a correct verification code message if cache has expired or if verification code does not exist" in new SetUp {
      val phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("enteredPhoneNumber", "enteredVerificationCode")
      when(
        validateServiceMock
          .validate(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .retrieveVerificationCode(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Future.successful(None))

      val result: Future[Result] = verifyService.verifyVerificationCode(phoneNumberAndVerificationCode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe CODE_VERIFY_FAILURE
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe CODE_NOT_RECOGNISED
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent: VerificationCheckAuditEvent =
        VerificationCheckAuditEvent("enteredPhoneNumber", "enteredVerificationCode", StatusCode.CODE_VERIFY_FAILURE)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationResult, expectedVerificationCheckAuditEvent)
    }

    "return Not verified if verification code does not match" in new SetUp {
      val phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("enteredPhoneNumber", "enteredVerificationCode")
      val phoneNumberVerificationCodeDataFromDb: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(phoneNumberAndVerificationCode.phoneNumber, "verificationcodethatdoesnotmatch")
      when(
        validateServiceMock
          .validate(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .retrieveVerificationCode(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Future.successful(Some(phoneNumberVerificationCodeDataFromDb)))

      val result: Future[Result] = verifyService.verifyVerificationCode(phoneNumberAndVerificationCode)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.CODE_VERIFY_FAILURE
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent: VerificationCheckAuditEvent =
        VerificationCheckAuditEvent("enteredPhoneNumber", "enteredVerificationCode", StatusCode.CODE_VERIFY_FAILURE)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationResult, expectedVerificationCheckAuditEvent)
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("enteredPhoneNumber", "enteredVerificationCode")
      when(
        validateServiceMock
          .validate(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Left(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER)))

      val result: Future[Result] = verifyService.verifyVerificationCode(phoneNumberAndVerificationCode)

      status(result) shouldBe BAD_REQUEST
      val errorResponse: VerificationStatus = contentAsJson(result).as[VerificationStatus]
      errorResponse.status shouldBe VALIDATION_ERROR
      errorResponse.message shouldBe StatusMessage.INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any(), any(), any())
    }

    "return internal sever error when datastore exception occurs on get" in new SetUp {
      val phoneNumberAndVerificationCode: PhoneNumberAndVerificationCode = PhoneNumberAndVerificationCode("enteredPhoneNumber", "enteredVerificationCode")
      when(
        validateServiceMock
          .validate(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        verificationServiceMock
          .retrieveVerificationCode(phoneNumberAndVerificationCode.phoneNumber)
      )
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))

      val result: Future[Result] = verifyService.verifyVerificationCode(phoneNumberAndVerificationCode)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe CODE_VERIFY_FAILURE
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any(), any(), any())
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()

    implicit val request: Request[JsValue] = FakeRequest(
      method = "POST",
      uri = "/some-uri",
      headers = Headers(HeaderNames.USER_AGENT -> "user-agent"),
      body = JsObject.empty
    )

    val verificationServiceMock: VerificationCodeService           = mock[VerificationCodeService]
    val validateServiceMock: ValidateService                       = mock[ValidateService]
    val userNotificationsConnectorMock: UserNotificationsConnector = mock[UserNotificationsConnector]
    val auditServiceMock: AuditService                             = mock[AuditService]
    val verificationCodeGeneratorMock: VerificationCodeGenerator   = mock[VerificationCodeGenerator]
    val metricsServiceMock: MetricsService                         = mock[MetricsService]
    val verificationCode                                           = "ABCDEF"
    when(verificationCodeGeneratorMock.generate()).thenReturn(verificationCode)

    val verifyService =
      new SendCodeService(verificationCodeGeneratorMock,
                          auditServiceMock,
                          verificationServiceMock,
                          validateServiceMock,
                          userNotificationsConnectorMock,
                          metricsServiceMock
      )
  }
}
