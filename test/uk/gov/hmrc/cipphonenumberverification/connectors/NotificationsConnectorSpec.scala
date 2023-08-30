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

package uk.gov.hmrc.cipphonenumberverification.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.cipphonenumberverification.config.{AppConfig, CircuitBreakerConfig, NotificationsConfig}
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PasscodeNotificationRequest
import uk.gov.hmrc.cipphonenumberverification.utils.TestActorSystem
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}

class NotificationsConnectorSpec
    extends AnyWordSpec
    with Matchers
    with WireMockSupport
    with ScalaFutures
    with EitherValues
    with HttpClientV2Support
    with TestActorSystem {

  val notificationUrl: String = "/notifications/sms"

  "NotificationsConnector" should {
    "send the passcode " in new SetUp {
      stubFor(
        post(urlEqualTo(notificationUrl))
          .willReturn(aResponse())
      )

      when(appConfigMock.phoneNotificationConfig).thenReturn(
        NotificationsConfig("http", wireMockHost, wireMockPort, UUID.randomUUID().toString, cbConfigData)
      )

      val now                     = System.currentTimeMillis()
      val phoneNumberPasscodeData = PhoneNumberPasscodeData("testPhoneNumber", "testPasscode")
      val phoneNumberRequest      = PasscodeNotificationRequest("testPhoneNumber", "Your Phone verification code: testPasscode")

      val result = notificationsConnector.sendPasscode(phoneNumberPasscodeData)
      whenReady(result) {
        res =>
          res.map(_.status shouldBe OK)
      }
      verify(
        postRequestedFor(urlEqualTo(notificationUrl)).withRequestBody(equalToJson(Json.toJson(phoneNumberRequest).toString()))
      )
    }
  }

  trait SetUp {
    implicit protected val hc: HeaderCarrier = HeaderCarrier()
    protected val appConfigMock              = mock[AppConfig]
    val cbConfigData                         = CircuitBreakerConfig("", 5, 5.toDuration, 30.toDuration, 5.toDuration, 1, 0)

    implicit class IntToDuration(timeout: Int) {
      def toDuration: FiniteDuration = Duration(timeout, java.util.concurrent.TimeUnit.SECONDS)
    }

    val notificationsConnector = new UserNotificationsConnector(
      httpClientV2,
      appConfigMock
    )
  }
}
