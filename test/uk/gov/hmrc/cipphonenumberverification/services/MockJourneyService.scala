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

package uk.gov.hmrc.cipphonenumberverification.services

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.models.JourneyMongoDocument
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait MockJourneyService extends MockFactory:
  this: TestSuite =>

  lazy val mockJourneyService: JourneyService = mock[JourneyService]

  object MockJourneyService:
    def initJourney(journeyUserAgent: String, journeyConfig: JourneyConfig)(response: Future[String])(implicit hc: HeaderCarrier): CallHandler3[String, JourneyConfig, HeaderCarrier, Future[String]] =
      (mockJourneyService
        .initJourney(_: String, _: JourneyConfig)(_: HeaderCarrier))
        .expects(journeyUserAgent, journeyConfig, *)
        .returning(response)

    def getJourneyDetails(journeyId: String)(response: Future[Option[JourneyMongoDocument]]): CallHandler1[String, Future[Option[JourneyMongoDocument]]] =
      (mockJourneyService
        .getJourneyDetails _)
        .expects(journeyId)
        .returning(response)
