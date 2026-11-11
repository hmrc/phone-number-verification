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

package uk.gov.hmrc.cipphonenumberverification.connectors

import org.scalamock.handlers.CallHandler4
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import play.api.libs.json.Writes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext

trait MockAuditConnector extends MockFactory:
  this: TestSuite =>

  lazy val mockAuditConnector: AuditConnector = mock[AuditConnector]

  object MockAuditConnector:
    def sendExplicitAuditEvent(auditType: String, detail: Map[String, String])(implicit hc: HeaderCarrier, ec: ExecutionContext): CallHandler4[
      String,
      Map[String, String],
      HeaderCarrier,
      ExecutionContext,
      Unit
    ] =
      (mockAuditConnector
        .sendExplicitAudit(_: String, _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
        .expects(auditType, detail, *, *)
        .returning(())