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

package uk.gov.hmrc.cipphonenumberverification.controllers

import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.ConfigLoader
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.mvc.Request
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.controllers.access.AccessChecker.{accessControlAllowListAbsoluteKey, accessControlEnabledAbsoluteKey}
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumberAndVerificationCode
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.services.SendCodeService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class VerifyCodeControllerSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "verifyCode" should {
    "delegate to verify service" in new SetUp {
      val verificationCode = PhoneNumberAndVerificationCode("07123456789", "123456")
      when(
        mockVerifyService
          .verifyVerificationCode(meq(verificationCode))(any[Request[JsValue]], any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Ok(Json.toJson(new VerificationStatus(StatusCode.CODE_SENT, StatusMessage.CODE_SENT)))))
      val result = controller.verifyCode(
        fakeRequest.withBody(Json.toJson(verificationCode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.CODE_SENT
    }

    "return 400 for empty telephone number" in new SetUp {
      val result = controller.verifyCode(
        fakeRequest.withBody(Json.toJson(PhoneNumberAndVerificationCode("", "test")))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe VALIDATION_ERROR
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE
    }

    "return 400 for invalid telephone number" in new SetUp {
      val result = controller.verifyCode(
        fakeRequest.withBody(Json.toJson(PhoneNumberAndVerificationCode("123", "test")))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "status").as[StatusCode.StatusCode] shouldBe VALIDATION_ERROR
      (contentAsJson(result) \ "message").as[StatusMessage.StatusMessage] shouldBe INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE
    }
  }

  trait SetUp {
    implicit protected val writes: OWrites[PhoneNumberAndVerificationCode] = Json.writes[PhoneNumberAndVerificationCode]
    protected val mockVerifyService                                        = mock[SendCodeService]
    protected val mockAppConfig                                            = mock[AppConfig]
    protected val fakeRequest                                              = FakeRequest().withHeaders("Authorization" -> "fake-token")

    {
      implicit val stringConfigLoader = ConfigLoader.stringLoader
      when(mockAppConfig.mustGetConfig(accessControlEnabledAbsoluteKey)).thenReturn(true.toString)
    }

    {
      implicit val stringSeqConfigLoader = ConfigLoader.seqStringLoader
      when(mockAppConfig.getConfig(accessControlAllowListAbsoluteKey)).thenReturn(Some(Seq("tester")))
    }

    protected val controller = new VerifyCodeController(Helpers.stubControllerComponents(), mockVerifyService, mockAppConfig)
  }
}
