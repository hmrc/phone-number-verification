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
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{contentAsJson, contentAsString, defaultAwaitTimeout, header, status}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes._
import uk.gov.hmrc.cipphonenumberverification.models.api.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models.domain.audit.AuditType.{PhoneNumberVerificationCheck, PhoneNumberVerificationRequest}
import uk.gov.hmrc.cipphonenumberverification.models.domain.audit.{VerificationCheckAuditEvent, VerificationRequestAuditEvent}
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndPasscode
import uk.gov.hmrc.cipphonenumberverification.models.http.govnotify.GovUkNotificationId
import uk.gov.hmrc.cipphonenumberverification.models.http.validation.ValidatedPhoneNumber
import uk.gov.hmrc.cipphonenumberverification.utils.DateTimeUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import java.time.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "verify" should {
    "return success if telephone number is valid" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)
        .returns(Future.successful(Right(HttpResponse(CREATED, Json.toJson(GovUkNotificationId("test-notification-id")).toString()))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe ACCEPTED
      contentAsString(result) shouldBe empty
      header("Location", result) shouldBe Some("/notifications/test-notification-id")

      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      // check what is sent to DAO
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called
      // Check what is sent to GovNotify
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"
      auditServiceMock wasNever called
      passcodeGeneratorMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return internal sever error when datastore exception occurs" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[Int] shouldBe PASSCODE_PERSISTING_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server has experienced an issue"
      // check what is sent to validation service
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      // check what is sent to the audit service
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
          expectedAuditEvent) was called
      // check what is sent to DAO
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called

      // Check NOTHING is sent to GovNotify
      govUkConnectorMock wasNever called
    }

    "return bad gateway when validation service returns 503" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))

      val result = verifyService.verifyPhoneNumber(phoneNumber)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_SERVICE_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return service unavailable when validation service throws connection exception" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.failed(new ConnectionException("")))

      val result = verifyService.verifyPhoneNumber(phoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_SERVICE_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return service unavailable when govUk notify service throws connection exception" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb)
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.failed(new ConnectionException("")))

      val result = verifyService.verifyPhoneNumber(PhoneNumber(enteredPhoneNumber.phoneNumber))

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_SERVICE_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return BadGateway if gov-notify returns internal server error" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("Server currently unavailable", INTERNAL_SERVER_ERROR))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_SERVICE_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }

    "return Service unavailable if gov-notify returns BadRequestError" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", BAD_REQUEST))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_API_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "External server currently unavailable"
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }

    "return Service unavailable if gov-notify returns Forbidden error" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", FORBIDDEN))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_API_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "External server currently unavailable"
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }

    "return Too Many Requests if gov-notify returns RateLimitError or TooManyRequestsError" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("External server currently unavailable", TOO_MANY_REQUESTS))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe TOO_MANY_REQUESTS
      (contentAsJson(result) \ "code").as[Int] shouldBe MESSAGE_THROTTLED_OUT.id
      (contentAsJson(result) \ "message").as[String] shouldBe "The request for the API is throttled as you have exceeded your quota"
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)(any[HeaderCarrier]) was called
    }

    "return indeterminate response if phone number is not a mobile" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Fixed-line")).toString())))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Indeterminate"
      (contentAsJson(result) \ "message").as[String] shouldBe "Only mobile numbers can be verified"
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      auditServiceMock wasNever called
      passcodeServiceMock wasNever called
      govUkConnectorMock wasNever called
    }

    "return response from service if response has not been handled" in new SetUp {
      val enteredPhoneNumber = PhoneNumber("test")
      val normalisedPhoneNumberAndPasscode = PhoneNumberAndPasscode("normalisedPhoneNumber", passcode)
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(normalisedPhoneNumberAndPasscode.phoneNumber, normalisedPhoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(enteredPhoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(normalisedPhoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.persistPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(phoneNumberPasscodeDataFromDb))
      govUkConnectorMock.sendPasscode(any[PhoneNumberPasscodeData])
        .returns(Future.successful(Left(UpstreamErrorResponse("Some random message from external service", CONFLICT))))

      val result = verifyService.verifyPhoneNumber(enteredPhoneNumber)

      status(result) shouldBe CONFLICT
      contentAsString(result) shouldBe empty
      validateConnectorMock.callService("test")(any[HeaderCarrier]) was called
      passcodeGeneratorMock.passcodeGenerator() was called
      val expectedAuditEvent = VerificationRequestAuditEvent("normalisedPhoneNumber", passcode)
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationRequest,
        expectedAuditEvent) was called
      passcodeServiceMock.persistPasscode(phoneNumberPasscodeDataFromDb) was called
      govUkConnectorMock.sendPasscode(phoneNumberPasscodeDataFromDb)(any[HeaderCarrier]) was called
      metricsServiceMock.recordMetric(any[String]) was called
    }
  }

  "verifyPasscode" should {
    "return verification error and passcode has expired message if passcode has expired" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      // assuming the passcode expiry config is set to 15 minutes
      val seventeenMinutes = Duration.ofMinutes(17).toMillis
      val passcodeExpiryWillHaveElapsed = now - seventeenMinutes
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(phoneNumberAndPasscode.phoneNumber, phoneNumberAndPasscode.passcode, passcodeExpiryWillHaveElapsed)
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(Some(phoneNumberPasscodeDataFromDb)))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "code").as[Int] shouldBe VERIFICATION_ERROR.id
      (contentAsJson(result) \ "message").as[String] shouldBe "The passcode has expired. Request a new passcode"
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", "Not verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return Verified if passcode matches" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(phoneNumberAndPasscode.phoneNumber, phoneNumberAndPasscode.passcode, now)
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(Some(phoneNumberPasscodeDataFromDb)))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Verified"
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", "Verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return verification error and enter a correct passcode message if cache has expired or if passcode does not exist" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(None))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "code").as[Int] shouldBe VERIFICATION_ERROR.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Enter a correct passcode"
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", "Not verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return Not verified if passcode does not match" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      val phoneNumberPasscodeDataFromDb = PhoneNumberPasscodeData(phoneNumberAndPasscode.phoneNumber, "passcodethatdoesnotmatch", now)
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(Some(phoneNumberPasscodeDataFromDb)))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
      // check what is sent to the audit service
      val expectedVerificationCheckAuditEvent = VerificationCheckAuditEvent("enteredPhoneNumber", "enteredPasscode", "Not verified")
      auditServiceMock.sendExplicitAuditEvent(PhoneNumberVerificationCheck,
        expectedVerificationCheckAuditEvent) was called
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"
      auditServiceMock wasNever called
    }

    "return internal sever error when datastore exception occurs on get" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndPasscode.phoneNumber, "Mobile")).toString())))
      passcodeServiceMock.retrievePasscode(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[Int] shouldBe PASSCODE_VERIFY_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server has experienced an issue"
      auditServiceMock wasNever called
    }

    "return bad gateway when validation service returns 503" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.successful(HttpResponse(SERVICE_UNAVAILABLE, "")))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe BAD_GATEWAY
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_SERVICE_FAIL.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Server currently unavailable"
    }

    "return service unavailable when validation service throws connection exception" in new SetUp {
      val phoneNumberAndPasscode = PhoneNumberAndPasscode("enteredPhoneNumber", "enteredPasscode")
      validateConnectorMock.callService(phoneNumberAndPasscode.phoneNumber)
        .returns(Future.failed(new ConnectionException("")))

      val result = verifyService.verifyPasscode(phoneNumberAndPasscode)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[Int] shouldBe EXTERNAL_SERVICE_FAIL.id
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
    val passcodeGeneratorMock: PasscodeGenerator = mock[PasscodeGenerator]
    val dateTimeUtilsMock: DateTimeUtils = mock[DateTimeUtils]
    val metricsServiceMock: MetricsService = mock[MetricsService]
    val passcode = "ABCDEF"
    passcodeGeneratorMock.passcodeGenerator().returns(passcode)
    val now = System.currentTimeMillis()
    dateTimeUtilsMock.getCurrentDateTime().returns(now)

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "passcode.expiry" -> 15,
        "cache.expiry" -> 120
      ))
    )

    val verifyService = new VerifyService(passcodeGeneratorMock, auditServiceMock, passcodeServiceMock, validateConnectorMock,
      govUkConnectorMock, metricsServiceMock, dateTimeUtilsMock, appConfig)
  }
}
