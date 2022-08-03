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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status._
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.models.{GovUkNotificationId, PhoneNumber, PhoneNumberAndOtp, ValidatedPhoneNumber}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "verify" should {
    "return success if telephone number is valid" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumber.phoneNumber, "Mobile")).toString())))
      passcodeService.persistPasscode(phoneNumber.phoneNumber)
        .returns(Future.successful(PhoneNumberAndOtp(phoneNumber.phoneNumber, "")))
      govUkConnectorMock.sendPasscode(any[PhoneNumberAndOtp])
        .returns(Future.successful(Right(HttpResponse(CREATED, Json.toJson(GovUkNotificationId("test-notification-id")).toString()))))
      val result = verifyService.verifyPhoneNumber(phoneNumber)
      status(result) shouldBe ACCEPTED
      (contentAsJson(result) \ "notificationId").as[String] shouldBe "test-notification-id"
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))
      val result = verifyService.verifyPhoneNumber(phoneNumber)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"
    }

    "return internal sever error when datastore exception occurs" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      validateConnectorMock.callService(phoneNumber.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumber.phoneNumber, "Mobile")).toString())))
      passcodeService.persistPasscode(phoneNumber.phoneNumber)
        .returns(Future.failed(new Exception("simulated database operation failure")))

      val result = verifyService.verifyPhoneNumber(PhoneNumber("test"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }
  }

  "verifyOtp" should {
    "return Verified if passcode matches" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeService.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(Some(phoneNumberAndOtp)))
      passcodeService.deletePasscode(phoneNumberAndOtp)
        .returns(Future.unit)
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Verified"

      passcodeService.deletePasscode(phoneNumberAndOtp) was called
    }

    "return Not verified if passcode does not exist" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeService.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(None))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
    }

    "return Not verified if passcode does not match" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "123456")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeService.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(Some(PhoneNumberAndOtp("", "654321"))))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
    }

    "return bad request if telephone number is invalid" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("test", "test")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(BAD_REQUEST, """{"res": "res"}""")))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "res").as[String] shouldBe "res"
    }

    "return internal sever error when datastore exception occurs on get" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeService.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.failed(new Exception("simulated database operation failure")))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }

    "return internal sever error when datastore exception occurs on delete" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      validateConnectorMock.callService(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(HttpResponse(OK, Json.toJson(ValidatedPhoneNumber(phoneNumberAndOtp.phoneNumber, "Mobile")).toString())))
      passcodeService.retrievePasscode(phoneNumberAndOtp.phoneNumber)
        .returns(Future.successful(Some(phoneNumberAndOtp)))
      passcodeService.deletePasscode(phoneNumberAndOtp)
        .returns(Future.failed(new Exception("simulated database operation failure")))
      val result = verifyService.verifyOtp(phoneNumberAndOtp)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    implicit val validatedPhoneNumberWrites: OWrites[ValidatedPhoneNumber] = Json.writes[ValidatedPhoneNumber]
    implicit val govUkNotificationIdWrites: OWrites[GovUkNotificationId] = Json.writes[GovUkNotificationId]
    val passcodeService: PasscodeService = mock[PasscodeService]
    val validateConnectorMock: ValidateConnector = mock[ValidateConnector]
    val govUkConnectorMock: GovUkConnector = mock[GovUkConnector]
    val verifyService = new VerifyService(passcodeService, validateConnectorMock, govUkConnectorMock)
  }
}
