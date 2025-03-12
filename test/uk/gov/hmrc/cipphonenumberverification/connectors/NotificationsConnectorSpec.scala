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
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers
import uk.gov.hmrc.cipphonenumberverification.config.{AppConfig, NotificationsConfig}
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberVerificationCodeData, VerificationCodeNotificationRequest}
import uk.gov.hmrc.cipphonenumberverification.utils.TestActorSystem
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.{Duration, FiniteDuration}

class NotificationsConnectorSpec
    extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with WireMockSupport
    with EitherValues
    with HttpClientV2Support
    with TestActorSystem {

  import VerificationCodeNotificationRequest.Implicits._

  val notificationUrl: String = "/notifications/sms"

  "NotificationsConnector" should {
    "send the verification code " in new SetUp {
      stubFor(
        post(urlEqualTo(notificationUrl))
          .willReturn(aResponse())
      )

      when(appConfigMock.phoneNotificationConfig).thenReturn(
        NotificationsConfig("http", wireMockHost, wireMockPort, UUID.randomUUID().toString)
      )

      val phoneNumberVerificationCodeData: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData("test-phone-number", "test-verification-code")

      val phoneNumberRequest: VerificationCodeNotificationRequest =
        VerificationCodeNotificationRequest("test-phone-number", "Your phone verification code is: test-verification-code")

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        Helpers.await(notificationsConnector.sendVerificationCode(phoneNumberVerificationCodeData))(Helpers.defaultAwaitTimeout)
      result shouldBe a[Right[_, _]]
      verify(
        postRequestedFor(urlEqualTo(notificationUrl)).withRequestBody(equalToJson(Json.toJson(phoneNumberRequest).toString()))
      )
    }
  }

  trait SetUp {
    implicit protected val hc: HeaderCarrier = HeaderCarrier()
    protected val appConfigMock: AppConfig   = mock[AppConfig]

    implicit class IntToDuration(timeout: Int) {
      def toDuration: FiniteDuration = Duration(timeout, java.util.concurrent.TimeUnit.SECONDS)
    }

    when(appConfigMock.appName).thenReturn("test-app")

    val notificationsConnector = new UserNotificationsConnector(
      httpClientV2,
      appConfigMock
    )
  }
}
