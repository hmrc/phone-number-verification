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

package uk.gov.hmrc.cipphonenumberverification.controllers.actions

import org.scalatest.matchers.should.Matchers.shouldBe
import play.api.mvc.Results
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.BaseSpec
import uk.gov.hmrc.cipphonenumberverification.services.MockJourneyService

import scala.concurrent.Future

class JourneyActionSpec extends BaseSpec with MockJourneyService:

  private val action = new JourneyActionImpl(mockJourneyService, Helpers.stubPlayBodyParsers())
  private val journeyId = "test-journey-id"

  ".apply(journeyId)" should:

    "pass the fetched journey and original request to the block" in:
      val request = FakeRequest("POST", s"/journey/$journeyId")
        .withHeaders("X-Test" -> "test-value")
        .withBody("phone-number")

      MockJourneyService.getJourneyDetails(journeyId)(
        Future.successful(Some(testJourneyConfigWithPhoneNumber))
      )

      val result = action(journeyId).invokeBlock(request, journeyRequest => {
        journeyRequest.journeyId shouldBe journeyId
        journeyRequest.journeyConfig shouldBe testJourneyConfigWithPhoneNumber
        journeyRequest.body shouldBe request.body
        journeyRequest.headers shouldBe request.headers
        journeyRequest.method shouldBe request.method
        journeyRequest.uri shouldBe request.uri
        Future.successful(Results.Ok)
      })

      status(result) shouldBe OK

    "return 404 without invoking the block when the journey does not exist" in:
      MockJourneyService.getJourneyDetails(journeyId)(Future.successful(None))

      val result = action(journeyId).invokeBlock(FakeRequest(), _ => fail("Block must not be invoked"))

      status(result) shouldBe NOT_FOUND

    "return 500 without invoking the block when fetching the journey fails" in:
      MockJourneyService.getJourneyDetails(journeyId)(
        Future.failed(new RuntimeException("Journey lookup failed"))
      )

      val result = action(journeyId).invokeBlock(FakeRequest(), _ => fail("Block must not be invoked"))

      status(result) shouldBe INTERNAL_SERVER_ERROR

    "propagate failures from the block" in:
      val exception = new RuntimeException("Block failed")
      MockJourneyService.getJourneyDetails(journeyId)(
        Future.successful(Some(testJourneyConfigWithPhoneNumber))
      )

      val result = action(journeyId).invokeBlock(FakeRequest(), _ => Future.failed(exception))

      result.failed.futureValue shouldBe exception
