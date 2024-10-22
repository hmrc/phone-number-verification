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
import play.api.http.{Status => HttpStatus}
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.{CODE_VERIFY_FAILURE, VALIDATION_ERROR}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.{CODE_NOT_RECOGNISED, INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE}
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage}
import uk.gov.hmrc.cipphonenumberverification.utils.DataSteps

class VerifyCodeIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with GuiceOneServerPerSuite with DataSteps {

  "/verify-code" should {
    "respond with 200 verified status with valid phone number and verification code" in {
      val phoneNumber = "07811123456"

      //generate PhoneNumberAndVerificationCode
      verify(phoneNumber).futureValue

      //retrieve PhoneNumberAndVerificationCode
      val maybePhoneNumberAndVerificationCode = retrieveVerificationCode("+447811123456").futureValue

      //verify PhoneNumberAndVerificationCode (sut)
      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/verify-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "$phoneNumber",
               "verificationCode": "${maybePhoneNumberAndVerificationCode.get.verificationCode}"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe HttpStatus.OK
      (response.json \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.CODE_VERIFIED
    }

    "respond with 200 not verified status with non existent phone number" in {
      //verify PhoneNumberAndVerificationCode (sut)
      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/verify-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "07811654321",
               "verificationCode": "123456"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe 200
      (response.json \ "status").as[StatusCode.StatusCode] shouldBe CODE_VERIFY_FAILURE
      (response.json \ "message").as[StatusMessage.StatusMessage] shouldBe CODE_NOT_RECOGNISED
    }

    "respond with 400 status when verification code not matched" in {
      val phoneNumber = "07811123456"

      //generate PhoneNumberAndVerificationCode
      verify(phoneNumber).futureValue

      //retrieve PhoneNumberAndVerificationCode
//      val maybePhoneNumberAndVerificationCode = retrieveVerificationCode("+447811123456").futureValue

      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/verify-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "+447811123456",
               "verificationCode": "not-matched-verification-code"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe HttpStatus.BAD_REQUEST
    }

    "respond with 400 status for invalid request" in {
      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/verify-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "07811654321",
               "verificationCode": ""
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe HttpStatus.BAD_REQUEST
      (response.json \ "status").as[StatusCode.StatusCode] shouldBe VALIDATION_ERROR
      (response.json \ "message").as[StatusMessage.StatusMessage] shouldBe INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE
    }

    "respond with 404 status for request with a verification code that does not match" in {
      val phoneNumber = "07811123654"

      //generate PhoneNumberAndVerificationCode
      verify(phoneNumber).futureValue

      //retrieve PhoneNumberAndVerificationCode
      val maybePhoneNumberAndVerificationCode = retrieveVerificationCode("+447811123654").futureValue

      val response =
        wsClient
          .url(s"$baseUrl/phone-number-verification/verify-code")
          .withHttpHeaders(("Authorization", "fake-token"))
          .withRequestFilter(AhcCurlRequestLogger())
          .post(Json.parse {
            s"""{
               "phoneNumber": "07811123654",
               "verificationCode": "ABCDEF"
               }""".stripMargin
          })
          .futureValue

      response.status shouldBe HttpStatus.NOT_FOUND
      (response.json \ "status").as[StatusCode.StatusCode] shouldBe StatusCode.CODE_VERIFY_FAILURE
      (response.json \ "message").as[StatusMessage.StatusMessage] shouldBe StatusMessage.CODE_NOT_RECOGNISED
    }
  }
}
