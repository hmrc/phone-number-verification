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

package uk.gov.hmrc.cipphonenumberverification

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

class NotificationsIntegrationSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite {

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl = s"http://localhost:$port"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure("metrics.enabled" -> false)
      .configure("auditing.enabled" -> false)
      .build()

  "notifications" ignore {
    "respond with 200 status with valid notification id" in {
      val verifyResponse =
        wsClient
          .url(s"$baseUrl/customer-insight-platform/phone-number/verify-details")
          .post(Json.parse {
            """{"phone-number": "07849123456"}""".stripMargin
          })
          .futureValue

      val notificationId = verifyResponse.json.\("notificationId")

      val response =
        wsClient
          .url(s"$baseUrl/customer-insight-platform/phone-number/notifications/$notificationId")
          .get
          .futureValue

      response.status shouldBe 200
      response.json.\("code") shouldBe 102
      response.json.\("message") shouldBe "Message has been sent"
    }

    "respond with 404 status when notification id not found" in {
      val response =
        wsClient
          .url(s"$baseUrl/customer-insight-platform/phone-number/notifications/a283b760-f173-11ec-8ea0-0242ac120002")
          .get
          .futureValue

      response.status shouldBe 404
    }
  }
}
