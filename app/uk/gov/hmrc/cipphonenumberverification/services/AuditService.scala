/*
 * Copyright 2026 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.Audit.AuditEvent
import uk.gov.hmrc.cipphonenumberverification.models.Audit.AuditType.AuditType
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton()
class AuditService @Inject() (auditConnector: AuditConnector, appConfig: AppConfig)(implicit ec: ExecutionContext) extends Logging {

  def sendExplicitAuditEvent[T <: AuditEvent](auditType: AuditType, auditEvent: T)(implicit hc: HeaderCarrier, writes: Writes[T]): Unit = {
    logger.info(s"Sending explicit auditType $auditType, detail: $auditEvent")

    val dataEvent = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = auditType.toString,
      tags = hc.appendToDefaultTags(hc.extraHeaders.toMap),
      detail = Json.toJson(auditEvent).as[JsObject]
    )

    auditConnector.sendExtendedEvent(dataEvent)
  }

  def sendExplicitAuditEvent(auditType: AuditType, detail: JsValue)(implicit hc: HeaderCarrier): Unit = {
    logger.info(s"Sending explicit auditType $auditType, detail: $detail")

    val dataEvent = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = auditType.toString,
      tags = hc.appendToDefaultTags(hc.extraHeaders.toMap),
      detail = Json.toJson(detail).as[JsObject]
    )

    auditConnector.sendExtendedEvent(dataEvent)
  }
}
