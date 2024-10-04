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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, JsValue}
import play.api.mvc.{Headers, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.audit.AuditType.PhoneNumberVerificationRequest
import uk.gov.hmrc.cipphonenumberverification.models.audit.VerificationRequestAuditEvent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}

class AuditServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import VerificationRequestAuditEvent.Implicits._

  "sendEvent" should {
    "send AuditEvent to audit service" in new SetUp {
      val auditEvent: VerificationRequestAuditEvent = VerificationRequestAuditEvent("test-phone-number", "test-verification-code")
      service.sendExplicitAuditEvent(PhoneNumberVerificationRequest, auditEvent)
      val extendedDataEventCaptor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

      verify(mockAuditConnector, atLeastOnce)
        .sendExtendedEvent(extendedDataEventCaptor.capture())(any(), any())

      val actualEDE = extendedDataEventCaptor.getValue
      actualEDE.auditSource shouldBe "user-agent"
      actualEDE.auditType shouldBe "PhoneNumberVerificationRequest"
      actualEDE.tags shouldBe empty
      actualEDE.redactionLog.redactedFields shouldBe empty
      actualEDE.truncationLog.truncatedFields shouldBe empty
      Try(UUID.fromString(actualEDE.eventId)) shouldBe a[Success[UUID]]
      val djs = actualEDE.detail
      (djs \ "phoneNumber").as[String] shouldBe "test-phone-number"
      (djs \ "verificationCode").as[String] shouldBe "test-verification-code"
    }
  }

  trait SetUp {
    implicit val ex: ExecutionContext = ExecutionContext.global
    implicit val hc: HeaderCarrier    = HeaderCarrier()

    implicit val request: Request[JsValue] = FakeRequest(
      method = "POST",
      uri = "/some-uri",
      headers = Headers(HeaderNames.USER_AGENT -> "user-agent"),
      body = JsObject.empty
    )
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockAppConfig: AppConfig           = mock[AppConfig]
    when(mockAppConfig.appName).thenReturn("user-agent")
    val service: AuditService = new AuditService(mockAuditConnector, mockAppConfig)
  }
}
