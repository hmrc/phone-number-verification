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

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.Writes
import uk.gov.hmrc.cipphonenumberverification.audit.AuditEvent
import uk.gov.hmrc.cipphonenumberverification.audit.AuditType.Type
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton()
class AuditService @Inject()(auditConnector: AuditConnector
                            )(implicit ec: ExecutionContext) extends Logging {

  def sendExplicitAuditEvent[T <: AuditEvent](auditType: Type, auditEvent: T)(implicit hc: HeaderCarrier, writes: Writes[T]): Unit = {
    logger.debug(s"Sending explicit audit event for $auditEvent")
    auditConnector.sendExplicitAudit(auditType.toString, auditEvent)
  }
}
