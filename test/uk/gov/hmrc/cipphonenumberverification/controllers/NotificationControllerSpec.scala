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
import play.api.http.Status
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.models.api.NotificationStatus
import uk.gov.hmrc.cipphonenumberverification.services.NotificationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class NotificationControllerSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  private implicit val writes: OWrites[NotificationStatus] = Json.writes[NotificationStatus]
  private val fakeRequest = FakeRequest()
  private val mockNotificationsService = mock[NotificationService]
  private val controller = new NotificationController(Helpers.stubControllerComponents(), mockNotificationsService)

  "status" should {
    "delegate to notification service" in {
      val notificationId = "notificationId"
      mockNotificationsService.status(notificationId)(any[HeaderCarrier])
        .returns(Future.successful(Ok(Json.toJson(NotificationStatus("test status", "test message")))))

      val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe Status.OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "test status"
      (contentAsJson(result) \ "message").as[String] shouldBe "test message"

      mockNotificationsService.status(notificationId)(any[HeaderCarrier]) was called
    }
  }
}
