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

import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import play.api.test.Helpers._
import uk.gov.hmrc.cipphonenumberverification.audit.VerificationDeliveryResultRequestAuditEvent
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.models.govnotify.response.GovUkNotificationStatusResponse
import uk.gov.hmrc.cipphonenumberverification.utils.GovNotifyUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

class NotificationServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "status" should {
    "return NotificationStatus for created status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("created")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 101
      (contentAsJson(result) \ "message").as[String] shouldBe "Message is in the process of being sent"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "created")

      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }


    "return NotificationStatus for sending status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("sending")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 102
      (contentAsJson(result) \ "message").as[String] shouldBe "Message has been sent"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "sending")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus for pending status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("pending")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 103
      (contentAsJson(result) \ "message").as[String] shouldBe "Message is in the process of being delivered"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "pending")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus for sent (international number) status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("sent")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 104
      (contentAsJson(result) \ "message").as[String] shouldBe "Message was sent successfully"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "sent")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus for delivered status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("delivered")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 105
      (contentAsJson(result) \ "message").as[String] shouldBe "Message was delivered successfully"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "delivered")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus for permanent-failure status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("permanent-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 106
      (contentAsJson(result) \ "message").as[String] shouldBe
        "Message was unable to be delivered by the network provider"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "permanent-failure")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus for temporary-failure status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("temporary-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 107
      (contentAsJson(result) \ "message").as[String] shouldBe
        "Message was unable to be delivered by the network provider"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "temporary-failure")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus for technical-failure status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("technical-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 200
      (contentAsJson(result) \ "code").as[Int] shouldBe 108
      (contentAsJson(result) \ "message").as[String] shouldBe "There is a problem with the notification vendor"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, expectedNotificationid, "technical-failure")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

    "return NotificationStatus when notification id not found" in new SetUp {
      val httpResponse = UpstreamErrorResponse("", Status.NOT_FOUND)
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Left(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe 404
      (contentAsJson(result) \ "code").as[String] shouldBe "NOT_FOUND"
      (contentAsJson(result) \ "message").as[String] shouldBe "No_data_found"
      mockGovNotifyUtils wasNever called
      val expectedVerificationDeliveryResultRequestAuditEvent: VerificationDeliveryResultRequestAuditEvent = VerificationDeliveryResultRequestAuditEvent("No_data_found", "No_data_found", notificationId, "No_data_found")
      mockAuditService.sendExplicitAuditEvent("PhoneNumberVerificationDeliveryResultRequest", expectedVerificationDeliveryResultRequestAuditEvent) was called
    }

  }

  trait SetUp {
    implicit val writes: OWrites[GovUkNotificationStatusResponse] = Json.writes[GovUkNotificationStatusResponse]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mockAuditService = mock[AuditService]
    val mockGovUkConnector = mock[GovUkConnector]
    val mockGovNotifyUtils = mock[GovNotifyUtils]
    val service = new NotificationService(mockGovNotifyUtils, mockAuditService, mockGovUkConnector)
    val notificationId = "740e5834-3a29-46b4-9a6f-16142fde533a"
    val expectedNotificationid = "740e5834-3a29-46b4-9a6f-16142fde533a"
    val passcode = "XYZABC"
    val expectedPhoneNumber = "+447900900123"
    val expectedGovNotifyResponseBody = "CIP Phone Number Verification Service: theTaxService needs to verify your telephone number.\n    Your telephone number verification code is ABCDEF.\n    Use this code within 10 minutes to verify your telephone number."
    mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(any[String]).returns(passcode)
  }

  private def buildGovNotifyResponseWithStatus(status: String): JsValue = {
    val source: String = Source.fromFile("test/uk/gov/hmrc/cipphonenumberverification/data/govNotifyGetMessageStatusResponse.json").getLines.mkString
    val json: JsValue = Json.parse(source)

    val jsonTransformerForStatusAndRest = (__).json.update(
      __.read[JsObject].map { o => o ++ Json.obj("status" -> JsString(status)) }
    )

    val updatedJsonWithUpdatedStatusAndRest = json.transform(jsonTransformerForStatusAndRest) match {
      case JsSuccess(x, _) => x
      case JsError(_) => fail()
    }

    updatedJsonWithUpdatedStatusAndRest
  }

}
