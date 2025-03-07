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

package uk.gov.hmrc.cipphonenumberverification.controllers

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberVerificationCodeData, ValidatedPhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.INVALID_TELEPHONE_NUMBER
import uk.gov.hmrc.cipphonenumberverification.models.response.VerificationStatus
import uk.gov.hmrc.cipphonenumberverification.services.{ValidateService, VerificationCodeService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestVerificationCodeControllerSpec extends PlaySpec with MockitoSugar {

  implicit val phoneNumberWrites: Writes[PhoneNumber]                                         = Json.writes[PhoneNumber]
  implicit val phoneNumberVerificationCodeDataFormat: Format[PhoneNumberVerificationCodeData] = Json.format[PhoneNumberVerificationCodeData]

  class Test {
    val validateService: ValidateService = mock[ValidateService]
    val service: VerificationCodeService = mock[VerificationCodeService]
    val appConfig: AppConfig             = mock[AppConfig]

    when(appConfig.getConfig[Seq[String]]("microservice.services.access-control.allow-list")).thenReturn(Some(Seq.empty))
    when(appConfig.mustGetConfig[Seq[String]]("microservice.services.access-control.allow-list")).thenReturn(Seq.empty)
    when(appConfig.mustGetConfig[String]("microservice.services.access-control.enabled")).thenReturn("false")
  }

  "retrieveVerificationCode returns Ok with verification code data when phone number is valid" in new Test {
    val controller = new TestVerificationCodeController(stubControllerComponents(), validateService, service, appConfig)

    val phoneNumber: PhoneNumber                                         = PhoneNumber("1234567890")
    val phoneNumberVerificationCodeData: PhoneNumberVerificationCodeData = PhoneNumberVerificationCodeData("1234567890", "1234")
    val validatedPhoneNumber: ValidatedPhoneNumber                       = ValidatedPhoneNumber(phoneNumber.phoneNumber, PhoneNumberType.MOBILE)

    when(validateService.validate(phoneNumber.phoneNumber)).thenReturn(Right(validatedPhoneNumber))
    when(service.retrieveVerificationCode(phoneNumber.phoneNumber)).thenReturn(Future.successful(Some(phoneNumberVerificationCodeData)))

    val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(phoneNumber))
    val result: Future[Result]        = controller.retrieveVerificationCode()(request)

    status(result) mustBe OK
    contentAsJson(result) mustBe Json.toJson(phoneNumberVerificationCodeData)
  }

  "retrieveVerificationCode returns BadRequest when phone number is invalid" in new Test {
    val controller = new TestVerificationCodeController(stubControllerComponents(), validateService, service, appConfig)

    val phoneNumber: PhoneNumber            = PhoneNumber("invalid")
    val validationError: VerificationStatus = VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER)

    when(validateService.validate(phoneNumber.phoneNumber)).thenReturn(Left(validationError))

    val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(phoneNumber))
    val result: Future[Result]        = controller.retrieveVerificationCode()(request)

    status(result) mustBe BAD_REQUEST
    contentAsJson(result) mustBe Json.toJson(validationError)
  }

  "retrieveVerificationCode returns NoContent when no verification code is found" in new Test {
    val controller = new TestVerificationCodeController(stubControllerComponents(), validateService, service, appConfig)

    val phoneNumber: PhoneNumber                   = PhoneNumber("1234567890")
    val validatedPhoneNumber: ValidatedPhoneNumber = ValidatedPhoneNumber(phoneNumber.phoneNumber, PhoneNumberType.MOBILE)

    when(validateService.validate(phoneNumber.phoneNumber)).thenReturn(Right(validatedPhoneNumber))
    when(service.retrieveVerificationCode(phoneNumber.phoneNumber)).thenReturn(Future.successful(None))

    val request: FakeRequest[JsValue] = FakeRequest().withBody(Json.toJson(phoneNumber))
    val result: Future[Result]        = controller.retrieveVerificationCode()(request)

    status(result) mustBe NO_CONTENT
  }

  "retrieveVerificationCode returns BadRequest when request body is invalid" in new Test {
    val controller = new TestVerificationCodeController(stubControllerComponents(), validateService, service, appConfig)

    val request: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj("invalid" -> "data"))
    val result: Future[Result]         = controller.retrieveVerificationCode()(request)

    status(result) mustBe BAD_REQUEST
    contentAsJson(result) mustBe Json.toJson(VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))
  }
}
