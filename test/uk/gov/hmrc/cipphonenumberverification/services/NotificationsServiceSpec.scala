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

package uk.gov.hmrc.cipphonenumberverification.services

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status
import play.api.libs.json.{Json, OWrites}
import play.api.test.Helpers._
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.models.GovUkNotificationStatus
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NotificationsServiceSpec extends AnyWordSpec with Matchers {

  private implicit val writes: OWrites[GovUkNotificationStatus] = Json.writes[GovUkNotificationStatus]
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val mockGovUkConnector = mock[GovUkConnector]
  private val service = new NotificationsService(mockGovUkConnector)
  private val notificationId = "notificationId"

  "status" should {
    "return NotificationStatus for created status" in {
      val govukNotificationStatus = GovUkNotificationStatus("created")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 101
      (contentAsJson(result) \ "message").as[String] shouldBe "Message is in the process of being sent"
    }

    "return NotificationStatus for sending status" in {
      val govukNotificationStatus = GovUkNotificationStatus("sending")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 102
      (contentAsJson(result) \ "message").as[String] shouldBe "Message has been sent"
    }

    "return NotificationStatus for pending status" in {
      val govukNotificationStatus = GovUkNotificationStatus("pending")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 103
      (contentAsJson(result) \ "message").as[String] shouldBe "Message is in the process of being delivered"
    }

    "return NotificationStatus for sent (international number) status" in {
      val govukNotificationStatus = GovUkNotificationStatus("sent")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 104
      (contentAsJson(result) \ "message").as[String] shouldBe "Message was sent successfully"
    }

    "return NotificationStatus for delivered status" in {
      val govukNotificationStatus = GovUkNotificationStatus("delivered")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 105
      (contentAsJson(result) \ "message").as[String] shouldBe "Message was delivered successfully"
    }

    "return NotificationStatus for permanent-failure status" in {
      val govukNotificationStatus = GovUkNotificationStatus("permanent-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 106
      (contentAsJson(result) \ "message").as[String] shouldBe
        "Message was unable to be delivered by the network provider"
    }

    "return NotificationStatus for temporary-failure status" in {
      val govukNotificationStatus = GovUkNotificationStatus("temporary-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 107
      (contentAsJson(result) \ "message").as[String] shouldBe
        "Message was unable to be delivered by the network provider"
    }

    "return NotificationStatus for technical-failure status" in {
      val govukNotificationStatus = GovUkNotificationStatus("technical-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govukNotificationStatus), Map.empty[String, Seq[String]])
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 108
      (contentAsJson(result) \ "message").as[String] shouldBe "There is a problem with the notification vendor"
    }

    "return NotificationStatus when notification id not found" in {
      val httpResponse = UpstreamErrorResponse("", Status.NOT_FOUND)
      when(mockGovUkConnector.callService(ArgumentMatchers.eq(notificationId))(any()))
        .thenReturn(Future.successful(Left(httpResponse)))

      val result = service.status(notificationId)
      status(result) shouldBe 404
      (contentAsJson(result) \ "message").as[String] shouldBe "Notification ID not found"
    }
  }
}
