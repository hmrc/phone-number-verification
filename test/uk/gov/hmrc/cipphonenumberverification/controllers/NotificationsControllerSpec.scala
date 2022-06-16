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
import play.api.http.Status
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.models.NotificationStatus
import uk.gov.hmrc.cipphonenumberverification.services.NotificationsService

import scala.concurrent.Future

class NotificationsControllerSpec extends AnyWordSpec with Matchers {

  private implicit val writes: OWrites[NotificationStatus] = Json.writes[NotificationStatus]
  private val fakeRequest = FakeRequest("GET", "/")
  private val mockNotificationsService = mock[NotificationsService]
  private val controller = new NotificationsController(Helpers.stubControllerComponents(), mockNotificationsService)

  "GET /notifications/" should {
    "return 200" in {
      val notificationId = "notificationId"
      when(mockNotificationsService.status(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Ok(Json.toJson(NotificationStatus(1, "test message")))))

      val result = controller.status(notificationId)(fakeRequest)
      status(result) shouldBe Status.OK
      (contentAsJson(result) \ "code").as[Int] shouldBe 1
      (contentAsJson(result) \ "message").as[String] shouldBe "test message"
    }
  }
}
