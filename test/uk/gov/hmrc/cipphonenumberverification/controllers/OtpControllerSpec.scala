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

package uk.gov.hmrc.cipphonenumberverification.controllers

import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.models.{Passcode, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.services.VerifyService

import scala.concurrent.Future

class OtpControllerSpec
  extends AnyWordSpec with Matchers {

  private implicit val writes: OWrites[Passcode] = Json.writes[Passcode]
  private val fakeRequest = FakeRequest()
  private val mockVerifyService = mock[VerifyService]
  private val controller = new OtpController(Helpers.stubControllerComponents(), mockVerifyService)

  "verifyOtp" should {
    "return 200" in {
      val passcode = Passcode("07811222333", "123456")
      when(mockVerifyService.verifyOtp(passcode)).thenReturn(Future.successful(Ok(Json.toJson(VerificationStatus("test")))))
      val result = controller.verifyOtp(
        fakeRequest.withBody(Json.toJson(passcode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "test"
    }

    "return 400 for invalid request" in {
      val result = controller.verifyOtp(
        fakeRequest.withBody(Json.toJson(Passcode("", "123456")))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "VALIDATION_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe "error.validation"
    }
  }
}
