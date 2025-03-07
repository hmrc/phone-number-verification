/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.libs.json.{Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.models.internal.ValidatedPhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumberAndVerificationCode.Implicits.writes
import uk.gov.hmrc.cipphonenumberverification.models.request.{PhoneNumber, PhoneNumberAndVerificationCode}
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.services.TestSendCodeService.testVerificationCode
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class TestSendCodeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "TestSendCodeService" should {

    implicit val phoneNumberWrites: Writes[PhoneNumber] = Json.writes[PhoneNumber]

    "sendCode returns Ok when phone number is verifiable" in {
      val validateService = mock[ValidateService]
      val controller      = new TestSendCodeService(validateService)

      val phoneNumber                                = PhoneNumber(TestSendCodeService.verifiablePhoneNumber)
      val validatedPhoneNumber: ValidatedPhoneNumber = ValidatedPhoneNumber(phoneNumber.phoneNumber, PhoneNumberType.MOBILE)

      when(validateService.validate(phoneNumber.phoneNumber)).thenReturn(Right(validatedPhoneNumber))

      val request = FakeRequest().withBody(Json.toJson(phoneNumber))
      val result  = controller.sendCode(phoneNumber)(request, HeaderCarrier())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(VerificationStatus(StatusCode.CODE_SENT, StatusMessage.CODE_SENT))
    }

    "sendCode returns BadRequest when phone number is invalid" in {
      val validateService = mock[ValidateService]
      val controller      = new TestSendCodeService(validateService)

      val phoneNumber = PhoneNumber(TestSendCodeService.invalidPhoneNumber)
      when(validateService.validate(phoneNumber.phoneNumber))
        .thenReturn(Left(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER)))

      val request = FakeRequest().withBody(Json.toJson(phoneNumber))
      val result  = controller.sendCode(phoneNumber)(request, HeaderCarrier())

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER))
    }

    "sendCode returns BadRequest when phone number is non-mobile" in {
      val validateService = mock[ValidateService]
      val controller      = new TestSendCodeService(validateService)

      val phoneNumber                                = PhoneNumber(TestSendCodeService.nonMobilePhoneNumber)
      val validatedPhoneNumber: ValidatedPhoneNumber = ValidatedPhoneNumber(phoneNumber.phoneNumber, PhoneNumberType.FIXED_LINE)

      when(validateService.validate(phoneNumber.phoneNumber)).thenReturn(Right(validatedPhoneNumber))

      val request = FakeRequest().withBody(Json.toJson(phoneNumber))
      val result  = controller.sendCode(phoneNumber)(request, HeaderCarrier())

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.ONLY_MOBILES_VERIFIABLE))
    }

    "verifyVerificationCode returns Ok when verification code matches" in {
      val validateService = mock[ValidateService]
      val controller      = new TestSendCodeService(validateService)

      val phoneNumberAndVerificationCode             = PhoneNumberAndVerificationCode(TestSendCodeService.verifiablePhoneNumber, TestSendCodeService.testVerificationCode)
      val validatedPhoneNumber: ValidatedPhoneNumber = ValidatedPhoneNumber(phoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)

      when(validateService.validate(phoneNumberAndVerificationCode.phoneNumber)).thenReturn(Right(validatedPhoneNumber))

      val request = FakeRequest().withBody(Json.toJson(phoneNumberAndVerificationCode))
      val result  = controller.verifyVerificationCode(phoneNumberAndVerificationCode)(request, HeaderCarrier())

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(VerificationStatus(StatusCode.CODE_VERIFIED, StatusMessage.CODE_VERIFIED))
    }

    "verifyVerificationCode returns NotFound when verification code does not match" in {
      val validateService = mock[ValidateService]
      val controller      = new TestSendCodeService(validateService)

      val phoneNumberAndVerificationCode = PhoneNumberAndVerificationCode(TestSendCodeService.nonVerifiableCodePhoneNumber, testVerificationCode)

      when(validateService.validate(phoneNumberAndVerificationCode.phoneNumber))
        .thenReturn(Right(ValidatedPhoneNumber(phoneNumberAndVerificationCode.phoneNumber, PhoneNumberType.MOBILE)))

      val request = FakeRequest().withBody(Json.toJson(phoneNumberAndVerificationCode))
      val result  = controller.verifyVerificationCode(phoneNumberAndVerificationCode)(request, HeaderCarrier())

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.toJson(VerificationStatus(StatusCode.CODE_VERIFY_FAILURE, StatusMessage.CODE_NOT_RECOGNISED))
    }

    "verifyVerificationCode returns BadRequest when phone number is invalid" in {
      val validateService = mock[ValidateService]
      val controller      = new TestSendCodeService(validateService)

      val phoneNumberAndVerificationCode = PhoneNumberAndVerificationCode(TestSendCodeService.invalidPhoneNumber, TestSendCodeService.testVerificationCode)
      when(validateService.validate(phoneNumberAndVerificationCode.phoneNumber))
        .thenReturn(Left(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER)))

      val request = FakeRequest().withBody(Json.toJson(phoneNumberAndVerificationCode))
      val result  = controller.verifyVerificationCode(phoneNumberAndVerificationCode)(request, HeaderCarrier())

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER))
    }
  }
}
