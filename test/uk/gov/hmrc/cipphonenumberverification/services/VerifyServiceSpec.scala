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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.mockito.{Answers, ArgumentCaptor}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Results.{BadRequest, Ok}
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.models.{Passcode, PhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerifyServiceSpec extends AnyWordSpec
  with Matchers
  with MockitoSugar {

  val validationSuccessfulResponseBody: JsObject = Json.obj("phoneNumber" -> "+447812345678", "phoneNumberType" -> "Mobile" )
  val validationFailureResponseBody: JsObject = Json.obj("code" -> "VALIDATION_ERROR", "message" -> "Enter a valid telephone number")
  val govUkSuccessResponseBody: JsObject = Json.obj("id" -> "test-notification-id")
  private val phoneNumberCaptor = ArgumentCaptor.forClass(classOf[PhoneNumber])
  private val passcodeArgCaptor = ArgumentCaptor.forClass(classOf[Passcode])

  "verify" should {
    "return success if telephone number is valid" ignore new SetUp {
      when(validateConnectorMock.callService(PhoneNumber("test"))(hc))
        .thenReturn(Future.successful(HttpResponse(200, validationSuccessfulResponseBody, Map("content-type" -> Seq("application/json")))))
      val phoneNumber: PhoneNumber = PhoneNumber("+441292123456")
      val passcode: Passcode = Passcode(phoneNumber = phoneNumber.phoneNumber, otp = "testPasscode")
      val savedPasscodeFromDb: Passcode = Passcode(phoneNumber = phoneNumber.phoneNumber, otp = passcode.otp)
      val futurePasscodeFromDb: Future[Passcode] = Future.successful(savedPasscodeFromDb)

      when(passcodeService.persistPasscode(phoneNumberCaptor.capture())(any[HeaderCarrier])).thenReturn(futurePasscodeFromDb)

      val futureGovUkSuccess = Future.successful(Right(HttpResponse(CREATED, govUkSuccessResponseBody.toString())))
      when(govUkConnectorMock.sendPasscode(any[Passcode])(any[HeaderCarrier])).thenReturn(futureGovUkSuccess)

      val result = verifyService.verifyPhoneNumber(PhoneNumber("test"))
      status(result) shouldBe ACCEPTED
      (contentAsJson(result) \ "notificationId").as[String] shouldBe "test-notification-id"

      val actualPhoneNumber: PhoneNumber = phoneNumberCaptor.getValue
      actualPhoneNumber.phoneNumber shouldBe "test"

      val actualPasscode: Passcode = passcodeArgCaptor.getValue
      actualPasscode.phoneNumber shouldBe "test"
      actualPasscode.otp.length shouldBe 6
    }

    "return failure if telephone number is invalid" ignore new SetUp {
      val phoneNumber = PhoneNumber("test")
      when(validateConnectorMock.callService(phoneNumber)(hc))
        .thenReturn(Future.successful(HttpResponse(400, validationFailureResponseBody, Map("content-type" -> Seq("application/json")))))
      val result = verifyService.verifyPhoneNumber(phoneNumber)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "VALIDATION_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe "Enter a valid telephone number"
    }

    "return internal sever error when datastore exception occurs" ignore new SetUp {
      when(validateConnectorMock.callService(PhoneNumber("test"))(hc))
        .thenReturn(Future.successful(HttpResponse(200, validationSuccessfulResponseBody, Map("content-type" -> Seq("application/json")))))
      val futureDbFailure: Future[Passcode] = Future.failed(new Exception("simulated database operation failure"))
      when(passcodeCacheRepositoryMock.persistPasscode(any[PhoneNumber], any[Passcode])(any[HeaderCarrier])).thenReturn(futureDbFailure)

      val result = verifyService.verifyPhoneNumber(PhoneNumber("test"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }
  }

  "verifyOtp" should {
    "return Verified if passcode is valid" ignore new SetUp {
      val passcode = Passcode("", "")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.successful(Some(passcode)))
      when(passcodeCacheRepositoryMock.delete(passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.unit)
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Verified"

      verify(passcodeCacheRepositoryMock).delete(passcode.phoneNumber)(DataKey("cip-phone-number-verification"))
    }

    "return Not verified if passcode is invalid" ignore new SetUp {
      val passcode = Passcode("", "")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.successful(None))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
    }

    "return Not verified if passcode does not match" ignore new SetUp {
      val passcode = Passcode("", "123456")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.successful(Some(Passcode("", "654321"))))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
    }

    "return internal sever error when datastore exception occurs on get" ignore new SetUp {
      val passcode = Passcode("", "")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }

    "return internal sever error when datastore exception occurs on delete" ignore new SetUp {
      val passcode = Passcode("", "")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.successful(Some(passcode)))
      when(passcodeCacheRepositoryMock.delete(passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }
  }

  trait SetUp {
    implicit val hc: HeaderCarrier = new HeaderCarrier()
    val passcodeCacheRepositoryMock: PasscodeCacheRepository = mock[PasscodeCacheRepository](Answers.RETURNS_DEEP_STUBS)
    val passcodeService: PasscodeService = mock[PasscodeService]
    val validateConnectorMock: ValidateConnector = mock[ValidateConnector]
    val govUkConnectorMock: GovUkConnector = mock[GovUkConnector]
    val verifyService = new VerifyService(passcodeService, validateConnectorMock, govUkConnectorMock)
  }
}
