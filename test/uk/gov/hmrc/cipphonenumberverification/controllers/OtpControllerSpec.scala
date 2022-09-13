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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.models.{PhoneNumberAndOtp, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.services.VerifyService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class OtpControllerSpec
  extends AnyWordSpec
    with Matchers
    with IdiomaticMockito {

  private implicit val writes: OWrites[PhoneNumberAndOtp] = Json.writes[PhoneNumberAndOtp]
  private val fakeRequest = FakeRequest()
  private val mockVerifyService = mock[VerifyService]
  private val controller = new OtpController(Helpers.stubControllerComponents(), mockVerifyService)

  "verifyOtp" should {
    "delegate to verify service" in {
      val passcode = PhoneNumberAndOtp("07123456789", "123456")
      mockVerifyService.verifyOtp(passcode)(any[HeaderCarrier])
        .returns(Future.successful(Ok(Json.toJson(VerificationStatus("test")))))
      val result = controller.verifyOtp(
        fakeRequest.withBody(Json.toJson(passcode))
      )
      status(result) shouldBe OK
      (contentAsJson(result) \ "status").as[String] shouldBe "test"
    }

    "return 400 for invalid request" in {
      val result = controller.verifyOtp(
        fakeRequest.withBody(Json.toJson(PhoneNumberAndOtp("", "test")))
      )
      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "VALIDATION_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe "Enter a valid passcode"
    }
  }
}
