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
import play.api.libs.json.{Json, Writes}
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.models.audit.AuditType.{PhoneNumberVerificationCheck, PhoneNumberVerificationRequest}
import uk.gov.hmrc.cipphonenumberverification.models.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberPasscodeData, ValidatedPhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.request.{PhoneNumber, PhoneNumberAndPasscode}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.{
  EXTERNAL_API_FAIL,
  EXTERNAL_SERVICE_FAIL,
  MESSAGE_THROTTLED_OUT,
  PASSCODE_PERSISTING_FAIL,
  PASSCODE_VERIFY_FAIL,
  VALIDATION_ERROR,
  VERIFICATION_ERROR,
  VERIFIED
}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.{
  EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE,
  INVALID_TELEPHONE_NUMBER,
  PASSCODE_NOT_RECOGNISED,
  SERVER_CURRENTLY_UNAVAILABLE,
  SERVER_EXPERIENCED_AN_ISSUE,
  SERVICE_THROTTLED_ERROR
}
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage, VerificationStatus}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import VerificationCheckAuditEvent.Implicits._
  import VerificationRequestAuditEvent.Implicits._

  "verify" should {
    "return success if telephone number is valid" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.successful(phoneNumberPasscodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendPasscode(phoneNumberPasscodeDataFromDb)
      )
        .thenReturn(
          Future.successful(
            Right(HttpResponse(OK, Json.toJson("""{"deliveryStatus" : "SUCCESSFUL"}"""), Map(HeaderNames.CONTENT_TYPE -> Seq(MimeTypes.JSON))))
          )
        )

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(VerificationStatus(VERIFIED, StatusMessage.VERIFIED))

      // header("Location", result) shouldBe Some("/notifications/test-notification-id")

      // check what is sent to validation service
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(metricsServiceMock, atLeastOnce()).recordSendNotificationSuccess()
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      // check what is sent to the audit service
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      // check what is sent to DAO
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(phoneNumberPasscodeDataFromDb)
      // Check what is sent to GovNotify
      verify(userNotificationsConnectorMock, atLeastOnce()).sendPasscode(meq(phoneNumberPasscodeDataFromDb))(any[HeaderCarrier])
    }

    "return error response if telephone number is invalid" in new SetUp {
      import uk.gov.hmrc.cipphonenumberverification.models.response.VerificationStatus._

      val enteredPhoneNumber: PhoneNumber = PhoneNumber("test")
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Left(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER)))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe BAD_REQUEST
      val errorResponse: VerificationStatus = contentAsJson(result).as[VerificationStatus]
      errorResponse.status shouldBe VALIDATION_ERROR
      errorResponse.message shouldBe INVALID_TELEPHONE_NUMBER
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any[HeaderCarrier], any())
      verify(passcodeGeneratorMock, never()).passcodeGenerator()
      verify(passcodeServiceMock, never()).persistPasscode(any())
      verify(passcodeServiceMock, never()).retrievePasscode(any())
      verify(userNotificationsConnectorMock, never()).sendPasscode(any())(any())
    }

    "return internal server error when datastore exception occurs" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe PASSCODE_PERSISTING_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      // check what is sent to validation service
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      // check what is sent to the audit service
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      // check what is sent to DAO
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(phoneNumberPasscodeDataFromDb)

      // Check NOTHING is sent to GovNotify
      verify(userNotificationsConnectorMock, never()).sendPasscode(any())(any())
    }

    "return BadGateway if user notifications returns internal server error" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.successful(phoneNumberPasscodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendPasscode(any[PhoneNumberPasscodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("Server currently unavailable", INTERNAL_SERVER_ERROR))))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe EXTERNAL_SERVICE_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVER_CURRENTLY_UNAVAILABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(meq(PhoneNumberVerificationRequest), meq(expectedAuditEvent))(any[HeaderCarrier], any())
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(meq(phoneNumberPasscodeDataFromDb))
      verify(userNotificationsConnectorMock, atLeastOnce()).sendPasscode(meq(phoneNumberPasscodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }

    "return Service unavailable if gov-notify returns BadRequestError" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.successful(phoneNumberPasscodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendPasscode(any[PhoneNumberPasscodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", BAD_REQUEST))))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe EXTERNAL_API_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(phoneNumberPasscodeDataFromDb)
      verify(userNotificationsConnectorMock, atLeastOnce()).sendPasscode(meq(phoneNumberPasscodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }

    "return Service unavailable if gov-notify returns Forbidden error" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.successful(phoneNumberPasscodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendPasscode(any[PhoneNumberPasscodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", FORBIDDEN))))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe EXTERNAL_API_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(phoneNumberPasscodeDataFromDb)
      verify(userNotificationsConnectorMock, atLeastOnce()).sendPasscode(meq(phoneNumberPasscodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }

    "return Too Many Requests if gov-notify returns RateLimitError or TooManyRequestsError" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.successful(phoneNumberPasscodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendPasscode(any[PhoneNumberPasscodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", TOO_MANY_REQUESTS))))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe TOO_MANY_REQUESTS
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe MESSAGE_THROTTLED_OUT
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVICE_THROTTLED_ERROR
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(meq(phoneNumberPasscodeDataFromDb))
      verify(userNotificationsConnectorMock, atLeastOnce()).sendPasscode(meq(phoneNumberPasscodeDataFromDb))(any[HeaderCarrier])
    }

    "return indeterminate response if phone number is not a mobile" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.FIXED_LINE)))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.INDETERMINATE
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe StatusMessage.ONLY_MOBILES_VERIFIABLE
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any(), any())
      verify(passcodeServiceMock, never()).persistPasscode(any())
      verify(passcodeServiceMock, never()).retrievePasscode(any())
      verify(userNotificationsConnectorMock, never()).sendPasscode(any())(any[HeaderCarrier])
    }

    "return response from service if response has not been handled" in new SetUp {
      val enteredPhoneNumber: PhoneNumber                          = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData =
        PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(enteredPhoneNumber.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .persistPasscode(any[PhoneNumberPasscodeData])
      )
        .thenReturn(Future.successful(phoneNumberPasscodeDataFromDb))
      when(
        userNotificationsConnectorMock
          .sendPasscode(any[PhoneNumberPasscodeData])(any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Left(UpstreamErrorResponse("Some random message from external service", CONFLICT))))

      val result: Future[Result] = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe CONFLICT
      contentAsString(result) shouldBe empty
      verify(validateServiceMock, atLeastOnce()).validate("test")
      verify(passcodeGeneratorMock, atLeastOnce()).passcodeGenerator()
      val expectedAuditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationRequest, expectedAuditEvent)
      verify(passcodeServiceMock, atLeastOnce()).persistPasscode(phoneNumberPasscodeDataFromDb)
      verify(userNotificationsConnectorMock, atLeastOnce()).sendPasscode(meq(phoneNumberPasscodeDataFromDb))(any[HeaderCarrier])
      verify(metricsServiceMock, atLeastOnce()).recordUpstreamError(any[UpstreamErrorResponse])
    }
  }

  "verifyPasscode" should {
    "return Verified if passcode matches" in new SetUp {
      val phoneNumberAndPasscode: PhoneNumberAndPasscode         = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData = PhoneNumberPasscodeData(phoneNumberAndPasscode.phoneNumber, phoneNumberAndPasscode.passcode)
      when(
        validateServiceMock
          .validate(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .retrievePasscode(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Future.successful(Some(phoneNumberPasscodeDataFromDb)))

      val result: Future[Result] = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.PASSCODE_VERIFIED
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent: VerificationCheckAuditEvent =
        VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", StatusCode.VERIFIED)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationCheck, expectedVerificationCheckAuditEvent)
    }

    "return verification error and enter a correct passcode message if cache has expired or if passcode does not exist" in new SetUp {
      val phoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      when(
        validateServiceMock
          .validate(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .retrievePasscode(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Future.successful(None))

      val result: Future[Result] = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe VERIFICATION_ERROR
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe PASSCODE_NOT_RECOGNISED
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent: VerificationCheckAuditEvent =
        VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", StatusCode.NOT_VERIFIED)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationCheck, expectedVerificationCheckAuditEvent)
    }

    "return Not verified if passcode does not match" in new SetUp {
      val phoneNumberAndPasscode: PhoneNumberAndPasscode         = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      val phoneNumberPasscodeDataFromDb: PhoneNumberPasscodeData = PhoneNumberPasscodeData(phoneNumberAndPasscode.phoneNumber, "passcodethatdoesnotmatch")
      when(
        validateServiceMock
          .validate(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .retrievePasscode(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Future.successful(Some(phoneNumberPasscodeDataFromDb)))

      val result: Future[Result] = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.PASSCODE_VERIFY_FAIL
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent: VerificationCheckAuditEvent =
        VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", StatusCode.NOT_VERIFIED)
      verify(auditServiceMock, atLeastOnce()).sendExplicitAuditEvent(PhoneNumberVerificationCheck, expectedVerificationCheckAuditEvent)
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val phoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      when(
        validateServiceMock
          .validate(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Left(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER)))

      val result: Future[Result] = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe BAD_REQUEST
      val errorResponse: VerificationStatus = contentAsJson(result).as[VerificationStatus]
      errorResponse.status shouldBe PASSCODE_VERIFY_FAIL
      errorResponse.message shouldBe PASSCODE_NOT_RECOGNISED
      verify(auditServiceMock, never()).sendExplicitAuditEvent(any(), any())(any(), any())
    }

    "return internal sever error when datastore exception occurs on get" in new SetUp {
      val phoneNumberAndPasscode: PhoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      when(
        validateServiceMock
          .validate(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, PhoneNumberType.MOBILE)))
      when(
        passcodeServiceMock
          .retrievePasscode(phoneNumberAndPasscode.phoneNumber)
      )
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))

      val result: Future[Result] = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe PASSCODE_VERIFY_FAIL
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe SERVER_EXPERIENCED_AN_ISSUE
      verify(auditServiceMock, never())
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier                                 = new HeaderCarrier()
    val passcodeServiceMock: PasscodeService                       = mock[PasscodeService]
    val validateServiceMock: ValidateService                       = mock[ValidateService]
    val userNotificationsConnectorMock: UserNotificationsConnector = mock[UserNotificationsConnector]
    val auditServiceMock: AuditService                             = mock[AuditService]
    val passcodeGeneratorMock: PasscodeGenerator                   = mock[PasscodeGenerator]
    val metricsServiceMock: MetricsService                         = mock[MetricsService]
    val passcode                                                   = "ABCDEF"
    when(passcodeGeneratorMock.passcodeGenerator()).thenReturn(passcode)

    val verifyService =
      new VerifyService(passcodeGeneratorMock, auditServiceMock, passcodeServiceMock, validateServiceMock, userNotificationsConnectorMock, metricsServiceMock)
  }
}
