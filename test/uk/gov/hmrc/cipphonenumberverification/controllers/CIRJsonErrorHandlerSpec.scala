/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cipphonenumberverification.controllers

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play._
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import scala.concurrent.ExecutionContext.Implicits.global

class CIRJsonErrorHandlerSpec extends PlaySpec with MockitoSugar {

  "CIRJsonErrorHandler" should {

    "handle NotFound error" in {
      val auditConnector = mock[AuditConnector]
      val httpAuditEvent = mock[HttpAuditEvent]
      val configuration  = mock[Configuration]
      val errorHandler   = new CIRJsonErrorHandler(auditConnector, httpAuditEvent, configuration)

      val request = FakeRequest(GET, "/non-existent-path")
      val result  = errorHandler.onClientError(request, NOT_FOUND, "Not Found")

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj("statusCode" -> NOT_FOUND, "message" -> "URI not found", "requested" -> "/non-existent-path")
    }

    "handle BadRequest error" in {
      val auditConnector = mock[AuditConnector]
      val httpAuditEvent = mock[HttpAuditEvent]
      val configuration  = mock[Configuration]
      val errorHandler   = new CIRJsonErrorHandler(auditConnector, httpAuditEvent, configuration)

      val request = FakeRequest(GET, "/bad-request")
      val result  = errorHandler.onClientError(request, BAD_REQUEST, "Invalid Json: Unrecognized token 'foo'")

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj("statusCode" -> BAD_REQUEST, "message" -> "bad request, cause: invalid json")
    }

    "handle generic client error" in {
      val auditConnector = mock[AuditConnector]
      val httpAuditEvent = mock[HttpAuditEvent]
      val configuration  = mock[Configuration]
      val errorHandler   = new CIRJsonErrorHandler(auditConnector, httpAuditEvent, configuration)

      val request = FakeRequest(GET, "/client-error")
      val result  = errorHandler.onClientError(request, FORBIDDEN, "Forbidden")

      status(result) mustBe FORBIDDEN
      contentAsJson(result) mustBe Json.obj("statusCode" -> FORBIDDEN, "message" -> "Forbidden")
    }

    "handle server error" in {
      val auditConnector = mock[AuditConnector]
      val httpAuditEvent = mock[HttpAuditEvent]
      val configuration  = mock[Configuration]
      val errorHandler   = new CIRJsonErrorHandler(auditConnector, httpAuditEvent, configuration)

      val request   = FakeRequest(GET, "/server-error")
      val exception = new RuntimeException("Internal server error")
      val result    = errorHandler.onServerError(request, exception)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj("statusCode" -> INTERNAL_SERVER_ERROR, "message" -> "Internal server error")
    }
  }
}
