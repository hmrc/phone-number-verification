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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.ConfigLoader
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.access.AccessChecker.{accessControlAllowListAbsoluteKey, accessControlEnabledAbsoluteKey}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.api.Verified
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndPasscode
import uk.gov.hmrc.cipphonenumberverification.services.VerifyService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client._

import scala.concurrent.Future

class PasscodeControllerSpec extends AnyWordSpec with Matchers with IdiomaticMockito {

  "verifyPasscode" should {
    "delegate to verify service" in new SetUp {
      val passcode = PhoneNumberAndPasscode("07123456789", "123456")
      mockVerifyService
        .verifyPasscode(passcode)(any[HeaderCarrier])
        .returns(Future.successful(Ok(Json.toJson(Verified("test")))))
      val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(passcode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "code").as[String] shouldBe "test"
    }

    "return 400 for invalid request" in new SetUp {
      val result = controller.verifyPasscode(
        fakeRequest.withBody(Json.toJson(PhoneNumberAndPasscode("", "test")))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[Int] shouldBe VALIDATION_ERROR.id
      (contentAsJson(result) \ "message").as[String] shouldBe "Enter a valid passcode"
    }
  }

  trait SetUp {
    implicit protected val writes: OWrites[PhoneNumberAndPasscode] = Json.writes[PhoneNumberAndPasscode]
    protected val mockVerifyService                                = mock[VerifyService]
    protected val mockAppConfig                                    = mock[AppConfig]
    protected val fakeRequest                                      = FakeRequest().withHeaders("Authorization" -> "fake-token")

    {
      implicit val stringConfigLoader = ConfigLoader.stringLoader
      when(mockAppConfig.mustGetConfig(accessControlEnabledAbsoluteKey)).thenReturn(true.toString)
    }

    {
      implicit val stringSeqConfigLoader = ConfigLoader.seqStringLoader
      when(mockAppConfig.getConfig(accessControlAllowListAbsoluteKey)).thenReturn(Some(Seq("tester")))
    }

    protected val controller = new VerifyPasscodeController(Helpers.stubControllerComponents(), mockVerifyService, mockAppConfig)
  }
}
