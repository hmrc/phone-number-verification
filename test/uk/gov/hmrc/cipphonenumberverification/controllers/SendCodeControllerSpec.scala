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
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Request
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.controllers.access.AccessChecker.{accessControlAllowListAbsoluteKey, accessControlEnabledAbsoluteKey}
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.services.{MetricsService, SendCodeService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SendCodeControllerSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import PhoneNumber.Implicits._

  "verify" should {
    "delegate to verify service" in new SetUp {
      val phoneNumber = PhoneNumber("")
      when(
        mockVerifyService
          .verifyPhoneNumber(meq(phoneNumber))(any[Request[JsValue]], any[HeaderCarrier])
      )
        .thenReturn(Future.successful(Ok))
      val result = controller.sendCode(
        fakeRequest.withBody(Json.toJson(phoneNumber))
      )
      status(result) shouldBe BAD_REQUEST
    }
  }

  trait SetUp {
    protected val mockVerifyService  = mock[SendCodeService]
    protected val mockMetricsService = mock[MetricsService]
    protected val mockAppConfig      = mock[AppConfig]
    protected val fakeRequest        = FakeRequest().withHeaders("User-Agent" -> "tester")

    {
      implicit val stringConfigLoader = ConfigLoader.stringLoader
      when(mockAppConfig.mustGetConfig(accessControlEnabledAbsoluteKey)).thenReturn(true.toString)
    }

    {
      implicit val stringSeqConfigLoader = ConfigLoader.seqStringLoader
      when(mockAppConfig.getConfig(accessControlAllowListAbsoluteKey)).thenReturn(Some(Seq("tester")))
    }

    protected val controller = new SendCodeController(Helpers.stubControllerComponents(), mockVerifyService, mockMetricsService, mockAppConfig)
  }
}
