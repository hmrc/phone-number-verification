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

package uk.gov.hmrc.cipphonenumberverification

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.models.internal.PhoneNumberVerificationCodeData
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.INVALID_TELEPHONE_NUMBER
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class SendCodeIntegrationSpec extends AnyWordSpec with MockitoSugar with Matchers with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite {

  private val mockNotificationsConnector: UserNotificationsConnector = mock[UserNotificationsConnector]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[UserNotificationsConnector].toInstance(mockNotificationsConnector))
      .build()

  private lazy val wsClient = app.injector.instanceOf[WSClient]
  private lazy val baseUrl  = s"http://localhost:$port"

  "/verify" should {
    "return 200 with valid telephone number" in {
      val phoneNumberVerificationCodeDataCaptor: ArgumentCaptor[PhoneNumberVerificationCodeData] =
        ArgumentCaptor.forClass(classOf[PhoneNumberVerificationCodeData])
      when(mockNotificationsConnector.sendVerificationCode(phoneNumberVerificationCodeDataCaptor.capture())(any())).thenReturn(
        Future.successful(
          Right(
            HttpResponse(OK, "{}")
          )
        )
      )

      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/send-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            """{"phoneNumber": "07811123456"}""".stripMargin
          })
          .futureValue

      phoneNumberVerificationCodeDataCaptor.getValue.phoneNumber shouldBe "+447811123456"
      response.status shouldBe 200
    }

    "respond with 400 status for invalid request" in {
      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/send-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{"phoneNumber": ""}""".stripMargin
          })
          .futureValue

      response.status shouldBe 400
      (response.json \ "status").as[StatusCode.StatusCode] shouldBe VALIDATION_ERROR
      (response.json \ "message").as[StatusMessage.StatusMessage] shouldBe INVALID_TELEPHONE_NUMBER
    }

    "respond with 400 status for request with invalid json payload" in {
      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/send-code")
          .withHttpHeaders(
            ("Authorization", "fake-token"),
            ("Content-Type", "application/json")
          )
          .withRequestFilter(AhcCurlRequestLogger())
          .post(s"""{"phoneNumber": }""")
          .futureValue

      response.status shouldBe 400
      (response.json \ "statusCode").as[Int] shouldBe 400
      (response.json \ "message").as[String].contains("Invalid Json: Unexpected character") shouldBe true
    }
  }
}
