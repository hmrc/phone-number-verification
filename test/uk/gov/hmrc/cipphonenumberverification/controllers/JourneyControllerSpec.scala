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

import org.scalatest.matchers.should.Matchers.shouldBe
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.BaseSpec
import uk.gov.hmrc.cipphonenumberverification.controllers.actions.FakeAllowListAction
import uk.gov.hmrc.cipphonenumberverification.services.MockJourneyService

import scala.concurrent.Future

class JourneyControllerSpec
  extends BaseSpec
    with MockJourneyService:

  private def controller(): JourneyController =
    new JourneyController(
      cc = Helpers.stubControllerComponents(),
      allowListAction = new FakeAllowListAction(Helpers.stubPlayBodyParsers())(isAllowed = true),
      journeyService = mockJourneyService
    )

  "The JourneyController" should :

    "return 201 Created when initJourney is called with valid journey config" in :
      val fakeRequest = FakeRequest("POST", "/journey/init")
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withBody(testJourneyConfigWithNoPhoneNumber)

      val redirectUrl = "http://test-redirect-url"

      MockJourneyService.initJourney(testJourneyConfigWithNoPhoneNumber)(
        Future.successful(redirectUrl)
      )

      val result = controller().initJourney(fakeRequest)

      status(result) shouldBe CREATED
      contentAsJson(result) shouldBe Json.toJson(redirectUrl)

    "return 400 Bad Request when initJourney is called with invalid journey config" in :
      val invalidRequest = FakeRequest("POST", "/journey/init")
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withBody(Json.obj("invalid" -> "data"))

      val result = controller().initJourney(invalidRequest)

      status(result) shouldBe BAD_REQUEST

    "return 500 Internal Server Error when initJourney fails" in :
      val fakeRequest = FakeRequest("POST", "/journey/init")
        .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withBody(testJourneyConfigWithNoPhoneNumber)

      MockJourneyService.initJourney(testJourneyConfigWithNoPhoneNumber)(
        Future.failed(new RuntimeException("Test exception"))
      )

      val result = controller().initJourney(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR


