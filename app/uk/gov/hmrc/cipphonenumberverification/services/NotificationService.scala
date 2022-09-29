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

import play.api.Logging
import play.api.http.HttpEntity
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, NOT_FOUND}
import play.api.libs.json.Json
import play.api.mvc.Results.{BadRequest, GatewayTimeout, NotFound, Ok, ServiceUnavailable}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.audit.AuditType.PhoneNumberVerificationDeliveryResultRequest
import uk.gov.hmrc.cipphonenumberverification.audit.VerificationDeliveryResultRequestAuditEvent
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.models.ErrorResponse.Codes
import uk.gov.hmrc.cipphonenumberverification.models.ErrorResponse.Codes.{EXTERNAL_API_FAIL, EXTERNAL_SERVICE_TIMEOUT, VALIDATION_ERROR}
import uk.gov.hmrc.cipphonenumberverification.models.govnotify.response.GovUkNotificationStatusResponse
import uk.gov.hmrc.cipphonenumberverification.models.{ErrorResponse, NotificationStatus}
import uk.gov.hmrc.cipphonenumberverification.utils.GovNotifyUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class NotificationService @Inject()(govNotifyUtils: GovNotifyUtils, auditService: AuditService, govUkConnector: GovUkConnector)
                                   (implicit val executionContext: ExecutionContext) extends Logging {

  private val NO_DATA_FOUND = "No_data_found"

  def status(notificationId: String)(implicit hc: HeaderCarrier): Future[Result] = {
    def success(response: HttpResponse) = {
      val govNotifyResponse: GovUkNotificationStatusResponse = response.json.as[GovUkNotificationStatusResponse]
      val phoneNumber = govNotifyResponse.phone_number
      val passcode = govNotifyUtils.extractPasscodeFromGovNotifyBody(govNotifyResponse.body)
      val deliveryStatus = govNotifyResponse.status
      val (notificationStatus, message) = deliveryStatus match {
        case "created" => ("CREATED", "Message is in the process of being sent")
        case "sending" => ("SENDING", "Message has been sent")
        case "pending" => ("PENDING", "Message is in the process of being delivered")
        case "sent" => ("SENT", "Message was sent successfully")
        case "delivered" => ("DELIVERED", "Message was delivered successfully")
        case "permanent-failure" => ("PERMANENT_FAILURE", "Message was unable to be delivered by the network provider")
        case "temporary-failure" => ("TEMPORARY_FAILURE", "Message was unable to be delivered by the network provider")
        case "technical-failure" => ("TECHNICAL_FAILURE", "There is a problem with the notification vendor")
      }
      auditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest,
        VerificationDeliveryResultRequestAuditEvent(phoneNumber, passcode, notificationId, deliveryStatus))
      Ok(Json.toJson(NotificationStatus(notificationStatus, message)))
    }

    def failure(err: UpstreamErrorResponse) = {
      err.statusCode match {
        case NOT_FOUND =>
          logger.warn("Notification Id not found")
          auditService.sendExplicitAuditEvent(PhoneNumberVerificationDeliveryResultRequest,
            VerificationDeliveryResultRequestAuditEvent(NO_DATA_FOUND, NO_DATA_FOUND, notificationId, NO_DATA_FOUND))
          NotFound(Json.toJson(ErrorResponse(Codes.NOTIFICATION_NOT_FOUND, "Notification Id not found")))
        case BAD_REQUEST =>
          logger.warn("Notification Id not valid")
          BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR, "Enter a valid notification Id")))
        case FORBIDDEN =>
          logger.warn(err.message)
          ServiceUnavailable(Json.toJson(ErrorResponse(EXTERNAL_API_FAIL, "External server currently unavailable")))
        case _ =>
          logger.error(err.message)
          Result.apply(ResponseHeader(err.statusCode), HttpEntity.NoEntity)
      }
    }

    govUkConnector.notificationStatus(notificationId).map {
      case Right(response) => success(response)
      case Left(err) => failure(err)
    } recover {
      case err =>
        logger.error(err.getMessage)
        GatewayTimeout(Json.toJson(ErrorResponse(EXTERNAL_SERVICE_TIMEOUT, "External server timeout")))
    }
  }
}
