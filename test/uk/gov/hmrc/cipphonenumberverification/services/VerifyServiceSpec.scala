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

import akka.stream.ConnectionException
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.audit.AuditType.{PhoneNumberVerificationCheck, PhoneNumberVerificationRequest}
import uk.gov.hmrc.cipphonenumberverification.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.{GovUkNotificationId, PhoneNumber, PhoneNumberAndOtp, ValidatedPhoneNumber}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "verify" should {
    "return success if telephone number is valid" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(normalisedPhoneNumberAndOtp))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(Right(HttpResponse(CREATED, Json.toJson(GovUkNotificationId("test-notification-id")).toString()))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe ACCEPTED
      (contentAsJson(result) \ "notificationId").as[String] shouldBe "test-notification-id"

      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
      otpServiceMock.otpGenerator() was called
      // check what is sent to the audit service
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", otp)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      // check what is sent to DAO
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp) was called
      // Check what is sent to GovNotify
      govUkConnectorMock.sendPasscode(normalisedPhoneNumberAndOtp)(any[HeaderCarrier]) was called
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"

      auditServiceMock wasNever called
      otpServiceMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return internal sever error when datastore exception occurs" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberAndOtp])
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "PASSCODE_PERSISTING_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server has experienced an issue"

      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      otpServiceMock.otpGenerator() was called
      // check what is sent to the audit service
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", otp)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
          expectedAuditEvent) was called
      // check what is sent to DAO
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp) was called

      // Check NOTHING is sent to GovNotify
      govUkConnectorMock wasNever called
    }

    "return bad gateway when validation service returns 503" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))
      val result = verifyService.verifyPhoneNumber(phoneNumber)
      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return service unavailable when validation service throws connection exception" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.failed(new ConnectionException("")))
      val result = verifyService.verifyPhoneNumber(phoneNumber)
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return service unavailable when govUk notify service throws connection exception" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp)
        .returns(Future.successful(normalisedPhoneNumberAndOtp))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.failed(new ConnectionException("")))
      val result = verifyService.verifyPhoneNumber(PhoneNumber(enteredPhoneNumber.phoneNumber))
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return BadGateway if gov-notify returns internal server error" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(normalisedPhoneNumberAndOtp))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(Left(UpstreamErrorResponse("Server currently unavailable", INTERNAL_SERVER_ERROR))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      otpServiceMock.otpGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", otp)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp) was called
      govUkConnectorMock.sendPasscode(normalisedPhoneNumberAndOtp)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }

    "return Service unavailable if gov-notify returns BadRequestError" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(normalisedPhoneNumberAndOtp))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", BAD_REQUEST))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_API_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "External server currently unavailable"

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      otpServiceMock.otpGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", otp)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp) was called
      govUkConnectorMock.sendPasscode(normalisedPhoneNumberAndOtp)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }

    "return Service unavailable if gov-notify returns Forbidden error" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(normalisedPhoneNumberAndOtp))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", FORBIDDEN))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_API_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "External server currently unavailable"

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      otpServiceMock.otpGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", otp)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp) was called
      govUkConnectorMock.sendPasscode(normalisedPhoneNumberAndOtp)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }

    "return Too Many Requests if gov-notify returns RateLimitError or TooManyRequestsError" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndOtp = PhoneNumberAndOtp("normalisedPhoneNumber", otp)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(normalisedPhoneNumberAndOtp))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", TOO_MANY_REQUESTS))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe TOO_MANY_REQUESTS
      (contentAsJson(result) \ "code").as[String] shouldBe "MESSAGE_THROTTLED_OUT"
      (contentAsJson(result) \ "message").as[String] shouldBe "The request for the API is throttled as you have exceeded your quota"

      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      otpServiceMock.otpGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", otp)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(normalisedPhoneNumberAndOtp) was called
      govUkConnectorMock.sendPasscode(normalisedPhoneNumberAndOtp)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }
  }

  "verifyOtp" should {
    "return Verified if passcode matches" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("enteredPhoneNumber", "enteredOtp")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(Some(phoneNumberAndOtp)))
      passcodeServiceMock.deletePasscode(phoneNumberAndOtp)
        .returns(Future.unit)

      val result = verifyService.verifyOtp(phoneNumberAndOtp)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Verified"

      passcodeServiceMock.deletePasscode(phoneNumberAndOtp) was called
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredOtp", "Verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return Not verified if passcode does not exist" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("enteredPhoneNumber", "enteredOtp")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(None))

      val result = verifyService.verifyOtp(phoneNumberAndOtp)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"

      passcodeServiceMock.deletePasscode(phoneNumberAndOtp) wasNever called
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredOtp", "Not verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return Not verified if passcode does not match" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("enteredPhoneNumber", "enteredOtp")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(Some(PhoneNumberAndOtp("", "654321"))))

      val result = verifyService.verifyOtp(phoneNumberAndOtp)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
      passcodeServiceMock.deletePasscode(phoneNumberAndOtp) wasNever called
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredOtp", "Not verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("test", "test")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"

      auditServiceMock wasNever called
    }

    "return internal sever error when datastore exception occurs on get" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyOtp(phoneNumberAndOtp)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "PASSCODE_VERIFY_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server has experienced an issue"
      passcodeServiceMock.deletePasscode(phoneNumberAndOtp) wasNever called
      auditServiceMock wasNever called
    }

    "return internal sever error when datastore exception occurs on delete" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("enteredPhoneNumber", "enteredOtp")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(Some(phoneNumberAndOtp)))
      passcodeServiceMock.deletePasscode(phoneNumberAndOtp)
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyOtp(phoneNumberAndOtp)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "PASSCODE_VERIFY_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server has experienced an issue"
      passcodeServiceMock.deletePasscode(phoneNumberAndOtp) was called
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredOtp", "Verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return bad gateway when validation service returns 503" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return service unavailable when validation service throws connection exception" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.failed(new ConnectionException("")))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    implicit val validatedPhoneNumberWrites: OWrites[ValidatedPhoneNumber] = Json.writes[ValidatedPhoneNumber]
    implicit val govUkNotificationIdWrites: OWrites[GovUkNotificationId] = Json.writes[GovUkNotificationId]
    val passcodeServiceMock: PasscodeService = mock[PasscodeService]
    val validateConnectorMock: ValidateConnector = mock[ValidateConnector]
    val govUkConnectorMock: GovUkConnector = mock[GovUkConnector]
    val auditServiceMock: AuditService = mock[AuditService]
    val otpServiceMock: OtpService = mock[OtpService]
    val metricsServiceMock: MetricsService = mock[MetricsService]
    val otp = "ABCDEF"
    otpServiceMock.otpGenerator().returns(otp)
    val verifyService = new VerifyService(otpServiceMock, auditServiceMock, passcodeServiceMock, validateConnectorMock, govUkConnectorMock, metricsServiceMock)
  }
}
