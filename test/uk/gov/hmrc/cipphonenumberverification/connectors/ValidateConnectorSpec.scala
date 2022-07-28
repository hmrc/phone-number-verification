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
import play.api.mvc.Results.{BadRequest, Ok}
import play.api.mvc.{ResponseHeader, Result}
import play.api.test.Helpers.{await, defaultAwaitTimeout, status}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumber
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ValidateConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support {

  val url: String = "/customer-insight-platform/phone-number/validate"

  "callService" should {
    "return HttpResponse OK for valid input" in new SetUp {
      val phoneNumber = PhoneNumber("test")

      stubFor(
        post(urlEqualTo(url))
          .willReturn(aResponse())
      )

      val result = validateConnector.callService(phoneNumber)

      await(result).status shouldBe OK

      verify(
        postRequestedFor(urlEqualTo(url))
          .withRequestBody(equalToJson(s"""{"phoneNumber": "${phoneNumber.phoneNumber}"}"""))
      )
    }
  }

  trait SetUp {

    protected implicit val hc: HeaderCarrier = HeaderCarrier()

    private val appConfig = new AppConfig(
      Configuration.from(Map(
        "microservice.services.cipphonenumber.validation.host" -> wireMockHost,
        "microservice.services.cipphonenumber.validation.port" -> wireMockPort,
        "microservice.services.cipphonenumber.validation.protocol" -> "http"
      ))
    )

    val validateConnector = new ValidateConnector(
      httpClientV2,
      appConfig
    )
  }
}
