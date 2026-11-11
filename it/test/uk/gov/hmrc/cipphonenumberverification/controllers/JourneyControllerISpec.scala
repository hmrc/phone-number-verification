/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.cipphonenumberverification.controllers

import org.scalatest.matchers.should.Matchers.{should, shouldBe}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.cipphonenumberverification.BaseISpec
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig

class JourneyControllerISpec extends BaseISpec:

  def executeRequest(requestBody: JsValue): WSResponse = {
    wsClient
      .url(s"$baseUrl/phone-number-verification/journey/init")
      .withHttpHeaders(
        HeaderNames.CONTENT_TYPE -> MimeTypes.JSON,
        HeaderNames.USER_AGENT -> testUserAgent,
      )
      .post(Json.stringify(requestBody))
      .futureValue
  }

  "POST /journey/init" when :

    "invalid journey configuration" should :

        "return 400 BAD REQUEST" in :
          val response = executeRequest(Json.obj("invalid" -> "data"))

          response.status shouldBe 400


    "valid journey configuration with no phone number" should :

        "return 201 CREATED with the expected enter phone number journey start URL" in :

          val response = executeRequest(Json.toJson(testJourneyConfigWithNoPhoneNumber))

          response.status shouldBe 201
          response.json.as[String] should fullyMatch regex
            s"/phone-number-verification/journey/[a-f0-9\\-]+/enter-phone-number"

    "valid journey configuration with phone number" should :

        "return 201 CREATED with the expected enter pass code journey start URL" in :

          val response = executeRequest(Json.toJson(testJourneyConfigWithPhoneNumber))

          response.status shouldBe 201
          response.json.as[String] should fullyMatch regex
            s"/phone-number-verification/journey/[a-f0-9\\-]+/enter-pass-code"