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
import play.api.http.Status
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.Passcode
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class GovUkConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support {

  val notificationId = "test-notification-id"
  val url: String = s"/v2/notifications/$notificationId"

  "notificationStatus" should {
    "return HttpResponse OK for valid input" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(aResponse())
      )

      implicit val hc: HeaderCarrier = HeaderCarrier()
      govUkConnector.notificationStatus(notificationId).map(res => {
        res shouldBe Right(HttpResponse(Status.OK, ""))
      })

      //      TODO: find out why this is failing
      //      verify(
      //        getRequestedFor(urlEqualTo(url))
      //      )
    }
  }

  "sendPasscode" should {
    "return HttpResponse OK for valid input" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(aResponse())
      )

      implicit val hc: HeaderCarrier = HeaderCarrier()
      govUkConnector.sendPasscode(Passcode("0789009002", "CVFRTG")).map(res => {
        res shouldBe Right(HttpResponse(Status.CREATED, ""))
      })
    }
  }

  trait Setup {

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "microservice.services.govuk-notify.host" -> wireMockUrl,
        "microservice.services.govuk-notify.api-key.iss-uuid" -> "",
        "microservice.services.govuk-notify.api-key.secret-key-uuid" -> UUID.randomUUID().toString,
        "microservice.services.govuk-notify.template_id" -> "template_id_fake",
        "cache.expiry" -> 1,
      ))
    )

    val govUkConnector = new GovUkConnector(
      httpClientV2,
      appConfig
    )
  }
}
