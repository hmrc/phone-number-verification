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

import org.scalamock.handlers.CallHandler3
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import play.api.libs.json.JsValue
import uk.gov.hmrc.cipphonenumberverification.models.Audit.AuditType.AuditType
import uk.gov.hmrc.http.HeaderCarrier

trait MockAuditService extends MockFactory:
  this: TestSuite =>

  lazy val mockAuditService: AuditService = mock[AuditService]

  object MockAuditService:

    def sendExplicitAuditEvent(auditType: AuditType, detail: JsValue)
                              (implicit hc: HeaderCarrier): CallHandler3[AuditType, JsValue, HeaderCarrier, Unit] =
      (mockAuditService
        .sendExplicitAuditEvent(_: AuditType, _: JsValue)(_: HeaderCarrier))
        .expects(auditType, detail, *)