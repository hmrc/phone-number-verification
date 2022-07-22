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
import play.api.libs.json.{JsObject, Json}
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

  val validationSuccessfulResponseBody: JsObject = Json.obj("phoneNumber" -> "+447812345678")
  val validationFailureResponseBody: JsObject = Json.obj("code" -> "VALIDATION_ERROR", "message" -> "Enter a valid telephone number")
  val govUkSuccessResponseBody: JsObject = Json.obj("id" -> "test-notification-id")
  private val phoneNumberCaptor = ArgumentCaptor.forClass(classOf[PhoneNumber])
  private val passcodeArgCaptor = ArgumentCaptor.forClass(classOf[Passcode])

  "verify" should {
    "return success if telephone number is valid" in new SetUp {
      when(validateConnectorMock.callService(PhoneNumber("test"))(hc)).thenReturn(Future.successful(Ok(validationSuccessfulResponseBody)))

      val phoneNumber: PhoneNumber = PhoneNumber("+441292123456")
      val passcode: Passcode = Passcode(phoneNumber = phoneNumber.phoneNumber, passcode = "testPasscode")
      val savedPasscodeFromDb: Passcode = Passcode(phoneNumber = phoneNumber.phoneNumber, passcode = passcode.passcode)
      val futurePasscodeFromDb: Future[Passcode] = Future.successful(savedPasscodeFromDb)
      when(passcodeCacheRepositoryMock.persistPasscode(phoneNumberCaptor.capture(),
        passcodeArgCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(futurePasscodeFromDb)

      val futureGovUkSuccess = Future.successful(Right(HttpResponse(CREATED, govUkSuccessResponseBody.toString())))
      when(govUkConnectorMock.sendPasscode(any[Passcode])(any[HeaderCarrier])).thenReturn(futureGovUkSuccess)

      val result = verifyService.verify(PhoneNumber("test"))
      status(result) shouldBe ACCEPTED
      (contentAsJson(result) \ "notificationId").as[String] shouldBe "test-notification-id"

      val actualPhoneNumber: PhoneNumber = phoneNumberCaptor.getValue
      actualPhoneNumber.phoneNumber shouldBe "test"

      val actualPasscode: Passcode = passcodeArgCaptor.getValue
      actualPasscode.phoneNumber shouldBe "test"
      actualPasscode.passcode.length shouldBe 6
    }

    "return failure if telephone number is invalid" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      when(validateConnectorMock.callService(phoneNumber)(hc))
        .thenReturn(Future.successful(BadRequest(validationFailureResponseBody)))
      val result = verifyService.verify(phoneNumber)
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "VALIDATION_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe "Enter a valid telephone number"
    }

    "return internal sever error when datastore exception occurs" in new SetUp {
      when(validateConnectorMock.callService(PhoneNumber("test"))(hc))
        .thenReturn(Future.successful(Ok(validationSuccessfulResponseBody)))
      val futureDbFailure: Future[Passcode] = Future.failed(new Exception("simulated database operation failure"))
      when(passcodeCacheRepositoryMock.persistPasscode(any[PhoneNumber], any[Passcode])(any[HeaderCarrier])).thenReturn(futureDbFailure)

      val result = verifyService.verify(PhoneNumber("test"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }

    "create 6 digit passcode" in new SetUp {
      verifyService.otpGenerator.forall(y => y.isUpper) shouldBe true
      verifyService.otpGenerator.forall(y => y.isLetter) shouldBe true

      val illegalChars = List('@', '£', '$', '%', '^', '&', '*', '(', ')', '-', '+')
      verifyService.otpGenerator.toList map (y => assertResult(illegalChars contains y)(false))

      verifyService.otpGenerator.length shouldBe 6
    }
  }

  "verifyOtp" should {
    "return Verified if passcode is valid" in new SetUp {
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

    "return Not verified if passcode is invalid" in new SetUp {
      val passcode = Passcode("", "")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.successful(None))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
    }

    "return Not verified if passcode does not match" in new SetUp {
      val passcode = Passcode("", "123456")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.successful(Some(Passcode("", "654321"))))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "Not verified"
    }

    "return internal sever error when datastore exception occurs on get" in new SetUp {
      val passcode = Passcode("", "")
      when(passcodeCacheRepositoryMock.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification")))
        .thenReturn(Future.failed(new Exception("simulated database operation failure")))
      val result = verifyService.verifyOtp(passcode)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "code").as[String] shouldBe "DATABASE_OPERATION_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "Database operation failed"
    }

    "return internal sever error when datastore exception occurs on delete" in new SetUp {
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
    val validateConnectorMock: ValidateConnector = mock[ValidateConnector]
    val govUkConnectorMock: GovUkConnector = mock[GovUkConnector]
    val verifyService = new VerifyService(passcodeCacheRepositoryMock, validateConnectorMock, govUkConnectorMock)
  }
}
