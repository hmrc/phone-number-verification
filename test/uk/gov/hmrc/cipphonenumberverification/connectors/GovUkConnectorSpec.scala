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

package uk.gov.hmrc.cipphonenumberverification.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.http.Status.OK
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.Passcode
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class GovUkConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support {

  val notificationId = "test-notification-id"
  val notificationsUrl: String = s"/v2/notifications/$notificationId"
  val smsUrl: String = "/v2/notifications/sms"

  "notificationStatus" should {
    "return HttpResponse OK for valid input" in new SetUp {
      stubFor(
        get(urlEqualTo(notificationsUrl))
          .willReturn(aResponse())
      )

      val result = govUkConnector.notificationStatus(notificationId)
      await(result).right.get.status shouldBe OK

      verify(
        getRequestedFor(urlEqualTo(notificationsUrl))
      )
    }
  }

  "sendPasscode" should {
    "return HttpResponse OK for valid input" in new SetUp {
      stubFor(
        post(urlEqualTo(smsUrl))
          .willReturn(aResponse())
      )

      val result = govUkConnector.sendPasscode(Passcode("test", "test"))
      await(result).right.get.status shouldBe OK

      verify(
        postRequestedFor(urlEqualTo(smsUrl)).withRequestBody(equalToJson(
          """
            {
              "phone_number": "test",
              "template_id": "template-id-fake",
              "personalisation": {
                "clientServiceName": "cip-phone-service",
                "passCode": "test",
                "timeToLive": "1"
              }
            }
            """))
      )
    }
  }

  trait SetUp {

    protected implicit val hc: HeaderCarrier = HeaderCarrier()

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "microservice.services.govuk-notify.host" -> wireMockUrl,
        "microservice.services.govuk-notify.api-key.iss-uuid" -> "",
        "microservice.services.govuk-notify.api-key.secret-key-uuid" -> UUID.randomUUID().toString,
        "microservice.services.govuk-notify.template_id" -> "template-id-fake",
        "cache.expiry" -> 1
      ))
    )

    val govUkConnector = new GovUkConnector(
      httpClientV2,
      appConfig
    )
  }
}
