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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND}
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes._
import uk.gov.hmrc.cipphonenumberverification.utils.DataSteps

class PasscodeIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite with DataSteps {

  "/verify/passcode" should {
    "respond with 200 verified status with valid phone number and passcode" in {
      val phoneNumber = "07811123456"

      //generate PhoneNumberAndPasscode
      verify(phoneNumber).futureValue

      //retrieve PhoneNumberAndPasscode
      val maybePhoneNumberAndPasscode = retrievePasscode("+447811123456").futureValue

      //verify PhoneNumberAndPasscode (sut)
      val response =
        wsClient
          .url(s"$baseUrl/phone-number/verify/passcode")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "$phoneNumber",
               "passcode": "${maybePhoneNumberAndPasscode.get.passcode}"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe 200
      (response.json \ "code").as[String] shouldBe "Verified"
    }

    "respond with 200 not verified status with non existent phone number" in {
      //verify PhoneNumberAndPasscode (sut)
      val response =
        wsClient
          .url(s"$baseUrl/phone-number/verify/passcode")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "07811654321",
               "passcode": "123456"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe 200
      (response.json \ "code").as[Int] shouldBe VERIFICATION_ERROR.id
      (response.json \ "message").as[String] shouldBe "Enter a correct passcode"
    }

    "respond with 400 status when passcode not matched" in {
      val phoneNumber = "07811123456"

      //generate PhoneNumberAndPasscode
      verify(phoneNumber).futureValue

      //retrieve PhoneNumberAndPasscode
      val maybePhoneNumberAndPasscode = retrievePasscode("+447811123456").futureValue

      val response =
        wsClient
          .url(s"$baseUrl/phone-number/verify/passcode")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "+447811123456",
               "passcode": "not-matched-passcode"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe BAD_REQUEST
    }

    "respond with 400 status for invalid request" in {
      val response =
        wsClient
          .url(s"$baseUrl/phone-number/verify/passcode")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "07811654321",
               "passcode": ""
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe BAD_REQUEST
      (response.json \ "code").as[Int] shouldBe VALIDATION_ERROR.id
      (response.json \ "message").as[String] shouldBe "Enter a valid passcode"
    }

    "respond with 404 status for request with a passcode that does not match" in {
      val phoneNumber = "07811123654"

      //generate PhoneNumberAndPasscode
      verify(phoneNumber).futureValue

      //retrieve PhoneNumberAndPasscode
      val maybePhoneNumberAndPasscode = retrievePasscode("+447811123654").futureValue
      println(s""">>> maybePhoneNumberAndPasscode: $maybePhoneNumberAndPasscode""")

      val response =
        wsClient
          .url(s"$baseUrl/phone-number/verify/passcode")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "07811123654",
               "passcode": "ABCDEF"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe NOT_FOUND
      (response.json \ "code").as[String] shouldBe "Not verified"
      (response.json \ "message").as[String] shouldBe "Not verified"
    }
  }
}
