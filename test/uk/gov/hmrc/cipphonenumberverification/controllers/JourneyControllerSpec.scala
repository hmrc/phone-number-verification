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
import uk.gov.hmrc.cipphonenumberverification.controllers.actions.{FakeAllowListAction, FakeJourneyAction}
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.services.MockJourneyService

import scala.concurrent.Future

class JourneyControllerSpec
  extends BaseSpec
    with MockJourneyService:

  private def controller(
                          isAllowed: Boolean = true,
                          configuredJourney: Either[Exception, Option[JourneyConfig]]
                        ): JourneyController =
    new JourneyController(
      cc = Helpers.stubControllerComponents(),
      allowListAction = new FakeAllowListAction(Helpers.stubPlayBodyParsers())(isAllowed = isAllowed),
      journeyService = mockJourneyService,
      withJourney = new FakeJourneyAction(Helpers.stubPlayBodyParsers())(
        journeyId = "existing-journey-id", journeyConfig = configuredJourney
      )
    )

  "The JourneyController" when :

    ".initJourney" when :
    
      "return 201 Created when initJourney is called with valid journey config" in :
        val fakeRequest = FakeRequest("POST", "/journey/init")
          .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(testJourneyConfigWithNoPhoneNumber)
  
        val redirectUrl = "http://test-redirect-url"
  
        MockJourneyService.initJourney(testJourneyConfigWithNoPhoneNumber)(
          Future.successful(redirectUrl)
        )
  
        val result = controller(configuredJourney = Right(Some(testJourneyConfigWithNoPhoneNumber))).initJourney(fakeRequest)
  
        status(result) shouldBe CREATED
        contentAsJson(result) shouldBe Json.toJson(redirectUrl)
  
      "return 400 Bad Request when initJourney is called with invalid journey config" in :
        val invalidRequest = FakeRequest("POST", "/journey/init")
          .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(Json.obj("invalid" -> "data"))
  
        val result = controller(configuredJourney = Right(Some(testJourneyConfigWithNoPhoneNumber))).initJourney(invalidRequest)
  
        status(result) shouldBe BAD_REQUEST
  
      "return 500 Internal Server Error when initJourney fails" in :
        val fakeRequest = FakeRequest("POST", "/journey/init")
          .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
          .withBody(testJourneyConfigWithNoPhoneNumber)
  
        MockJourneyService.initJourney(testJourneyConfigWithNoPhoneNumber)(
          Future.failed(new RuntimeException("Test exception"))
        )
  
        val result = controller(configuredJourney = Left(new RuntimeException("Test exception"))).initJourney(fakeRequest)
  
        status(result) shouldBe INTERNAL_SERVER_ERROR
      
    ".journeyDetails" when :

      "return 403 Forbidden without fetching the journey when access is denied" in :
        val journeyId = "existing-journey-id"
        val fakeRequest = FakeRequest("GET", s"/journey/$journeyId")

        val result = controller(isAllowed = false, configuredJourney = Right(Some(testJourneyConfigWithNoPhoneNumber))).journeyDetails(journeyId)(fakeRequest)

        status(result) shouldBe FORBIDDEN
      
      "return 200 OK with journey details when journeyDetails is called for an existing journey" in :
        val journeyId = "existing-journey-id"
        val fakeRequest = FakeRequest("GET", s"/journey/$journeyId")
  
        val result = controller(configuredJourney = Right(Some(testJourneyConfigWithPhoneNumber))).journeyDetails(journeyId)(fakeRequest)
  
        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(testJourneyConfigWithPhoneNumber)
  
      "return 404 Not Found when journeyDetails is called for a missing journey" in :
        val journeyId = "missing-journey-id"
        val fakeRequest = FakeRequest("GET", s"/journey/$journeyId")

        val result = controller(configuredJourney = Right(None)).journeyDetails(journeyId)(fakeRequest)
  
        status(result) shouldBe NOT_FOUND
  
      "return 500 Internal Server Error when journeyDetails fails" in :
        val journeyId = "failing-journey-id"
        val fakeRequest = FakeRequest("GET", s"/journey/$journeyId")
  
        val result = controller(configuredJourney = Left(new RuntimeException("Test exception"))).journeyDetails(journeyId)(fakeRequest)
  
        status(result) shouldBe INTERNAL_SERVER_ERROR
