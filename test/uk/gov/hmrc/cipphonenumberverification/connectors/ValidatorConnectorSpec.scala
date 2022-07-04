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
import play.api.mvc.Results.Ok
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumber
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class ValidateConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support {

  val url: String = "/customer-insight-platform/phone-number/validate-format"

  "ValidatorConnector" when {
    "callService is called" should {
      "return HttpResponse OK for valid input" in new Setup {
        val phoneNumber = PhoneNumber("07843274323")
        stubFor(
          post(urlEqualTo(url))
            .willReturn(aResponse())
        )

        implicit val hc = HeaderCarrier()
        validateConnector.callService(phoneNumber)
          .futureValue shouldBe Ok

        verify(
          postRequestedFor(urlEqualTo(url))
            .withRequestBody(equalToJson(s"""{"phoneNumber": "07843274323"}"""))
        )
      }
    }
  }

  trait Setup {

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "microservice.services.cipphonenumber.validation.host" -> wireMockHost,
        "microservice.services.cipphonenumber.validation.port" -> wireMockPort,
        "microservice.services.cipphonenumber.validation.protocol" -> "http",
      ))
    )

    val validateConnector = new ValidateConnector(
      httpClientV2,
      appConfig
    )
  }
}