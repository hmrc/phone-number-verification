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

import akka.stream.ConnectionException
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, GATEWAY_TIMEOUT, NOT_FOUND, OK, SERVICE_UNAVAILABLE}
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import play.api.test.Helpers._
import uk.gov.hmrc.cipphonenumberverification.audit.AuditType.PhoneNumberVerificationDeliveryResultRequest
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

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "CREATED"
      (contentAsJson(result) \ "message").as[String] shouldBe "Message is in the process of being sent"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "created")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for sending status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("sending")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "SENDING"
      (contentAsJson(result) \ "message").as[String] shouldBe "Message has been sent"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "sending")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for pending status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("pending")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "PENDING"
      (contentAsJson(result) \ "message").as[String] shouldBe "Message is in the process of being delivered"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "pending")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for sent (international number) status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("sent")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "SENT"
      (contentAsJson(result) \ "message").as[String] shouldBe "Message was sent successfully"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "sent")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for delivered status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("delivered")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "DELIVERED"
      (contentAsJson(result) \ "message").as[String] shouldBe "Message was delivered successfully"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "delivered")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for permanent-failure status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("permanent-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "PERMANENT_FAILURE"
      (contentAsJson(result) \ "message").as[String] shouldBe
        "Message was unable to be delivered by the network provider"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "permanent-failure")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for temporary-failure status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("temporary-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "TEMPORARY_FAILURE"
      (contentAsJson(result) \ "message").as[String] shouldBe
        "Message was unable to be delivered by the network provider"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "temporary-failure")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus for technical-failure status" in new SetUp {
      val govUkNotificationStatus = buildGovNotifyResponseWithStatus("technical-failure")
      val httpResponse = HttpResponse(Status.OK, Json.toJson(govUkNotificationStatus), Map.empty[String, Seq[String]])
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Right(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe OK
      (contentAsJson(result) \ "notificationStatus").as[String] shouldBe "TECHNICAL_FAILURE"
      (contentAsJson(result) \ "message").as[String] shouldBe "There is a problem with the notification vendor"
      mockGovNotifyUtils.extractPasscodeFromGovNotifyBody(expectedGovNotifyResponseBody) was called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent(expectedPhoneNumber, passcode, notificationId, "technical-failure")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return NotificationStatus when notification id not found" in new SetUp {
      val httpResponse = UpstreamErrorResponse("", Status.NOT_FOUND)
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Left(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "code").as[String] shouldBe "NOTIFICATION_NOT_FOUND"
      (contentAsJson(result) \ "message").as[String] shouldBe "Notification Id not found"
      mockGovNotifyUtils wasNever called
      val expectedAuditEvent = VerificationDeliveryResultRequestAuditEvent("No_data_found", "No_data_found", notificationId, "No_data_found")
      mockAuditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest, expectedAuditEvent) was called
    }

    "return ErrorResponse when notification id is not valid uuid" in new SetUp {
      val httpResponse = UpstreamErrorResponse("", Status.BAD_REQUEST)
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Left(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String] shouldBe "VALIDATION_ERROR"
      (contentAsJson(result) \ "message").as[String] shouldBe "Enter a valid notification Id"
      mockGovNotifyUtils wasNever called
      mockAuditService wasNever called
    }

    "return ErrorResponse when gov uk notify returns 403" in new SetUp {
      val httpResponse = UpstreamErrorResponse("", Status.FORBIDDEN)
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.successful(Left(httpResponse)))

      val result = service.status(notificationId)

      status(result) shouldBe SERVICE_UNAVAILABLE
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_API_FAIL"
      (contentAsJson(result) \ "message").as[String] shouldBe "External server currently unavailable"
      mockGovNotifyUtils wasNever called
      mockAuditService wasNever called
    }

    "return Gateway timeout if gov-notify connection times-out" in new SetUp {
      mockGovUkConnector.notificationStatus(notificationId)
        .returns(Future.failed(new ConnectionException("")))

      val result = service.status(notificationId)

      status(result) shouldBe GATEWAY_TIMEOUT
      (contentAsJson(result) \ "code").as[String] shouldBe "EXTERNAL_SERVICE_TIMEOUT"
      (contentAsJson(result) \ "message").as[String] shouldBe "External server timeout"
      mockGovNotifyUtils wasNever called
      mockAuditService wasNever called
    }
  }

  trait SetUp {
    implicit val writes: OWrites[GovUkNotificationStatusResponse] = Json.writes[GovUkNotificationStatusResponse]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mockAuditService = mock[AuditService]
    val mockGovUkConnector = mock[GovUkConnector]
    val mockGovNotifyUtils = mock[GovNotifyUtils]
    val service = new NotificationService(mockGovNotifyUtils, mockAuditService, mockGovUkConnector)
    val notificationId = "test"
    val passcode = "XYZABC"
    val expectedPhoneNumber = "+447900900123"
    val expectedGovNotifyResponseBody =
      """CIP Phone Number Verification Service: theTaxService needs to verify your telephone number.
        |Your telephone number verification code is ABCDEF.
        |Use this code within 10 minutes to verify your telephone number.""".stripMargin
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
