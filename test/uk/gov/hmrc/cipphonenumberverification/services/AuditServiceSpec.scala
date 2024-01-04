/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cipphonenumberverification.models.audit.AuditType.PhoneNumberVerificationRequest
import uk.gov.hmrc.cipphonenumberverification.models.audit.VerificationRequestAuditEvent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext

class AuditServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import VerificationRequestAuditEvent.Implicits._

  "sendEvent" should {
    "send AuditEvent to audit service" in new SetUp {
      val auditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("myPhoneNumber", "myPasscode")
      service.sendExplicitAuditEvent(PhoneNumberVerificationRequest, auditEvent)

      verify(mockAuditConnector, atLeastOnce)
        .sendExplicitAudit[VerificationRequestAuditEvent](meq(PhoneNumberVerificationRequest.toString), meq(auditEvent))(any(), any(), any())
    }
  }

  trait SetUp {
    implicit val ex: ExecutionContext      = ExecutionContext.global
    implicit val hc: HeaderCarrier         = HeaderCarrier()
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val service: AuditService              = new AuditService(mockAuditConnector)
  }
}
