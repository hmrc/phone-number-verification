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
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.OK
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipphonenumberverification.config.{AppConfig, CipValidationConfig, CircuitBreakerConfig}
import uk.gov.hmrc.cipphonenumberverification.models.api.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.utils.TestActorSystem
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ValidateConnectorSpec extends AnyWordSpec
  with Matchers
  with WireMockSupport
  with ScalaFutures
  with HttpClientV2Support
  with TestActorSystem {

  val url: String = "/customer-insight-platform/phone-number/validate"

  "callService" should {

    "delegate to http client" in new SetUp {
      val phoneNumber = PhoneNumber("test")

      stubFor(post(urlEqualTo(url)).willReturn(aResponse()))
      when(appConfigMock.validationConfig).thenReturn(CipValidationConfig(
        "http", wireMockHost, wireMockPort, "fake-token", cbConfigData))
      when(appConfigMock.cacheExpiry).thenReturn(1)

      val result = validateConnector.callService(phoneNumber.phoneNumber)

      await(result).status shouldBe OK

      verify(
        postRequestedFor(urlEqualTo(url))
          .withRequestBody(equalToJson(s"""{"phoneNumber": "${phoneNumber.phoneNumber}"}"""))
      )
    }
  }

  trait SetUp {

    protected implicit val hc: HeaderCarrier = HeaderCarrier()

    protected val appConfigMock = mock[AppConfig]
    val cbConfigData = CircuitBreakerConfig("", 5, 5.toDuration, 30.toDuration, 5.toDuration, 1, 0)
    implicit class IntToDuration(timeout: Int) {
      def toDuration = Duration(timeout, java.util.concurrent.TimeUnit.SECONDS)
    }

    val validateConnector = new ValidateConnector(
      httpClientV2,
      appConfigMock
    )
  }
}
