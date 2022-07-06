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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status.OK
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.services.VerifyService

import scala.concurrent.Future

class VerificationControllerSpec
  extends AnyWordSpec with Matchers {

  private implicit val writes: OWrites[PhoneNumber] = Json.writes[PhoneNumber]
  private val fakeRequest = FakeRequest()
  private val mockVerifyService = mock[VerifyService]
  private val controller = new VerificationController(Helpers.stubControllerComponents(), mockVerifyService)

  "POST /verify-details" should {
    "return 200 for valid request" in {
      when(mockVerifyService.verify(ArgumentMatchers.eq(PhoneNumber("")))(any())).thenReturn(Future.successful(Ok))
      val result = controller.verify(
        fakeRequest.withBody(Json.toJson(PhoneNumber("")))
      )
      status(result) shouldBe OK
    }
  }
}
