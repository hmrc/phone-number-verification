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

package uk.gov.hmrc.cipphonenumberverification.connectors

import org.scalamock.handlers.{CallHandler1, CallHandler2, CallHandler3}
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.JourneyRepository
import uk.gov.hmrc.cipphonenumberverification.repositories.models.JourneyMongoDocument

import scala.concurrent.Future

trait MockJourneyRepository extends MockFactory:
  this: TestSuite =>

  lazy val mockJourneyRepository: JourneyRepository = mock[JourneyRepository]

  object MockJourneyRepository:

    def set(journeyId: String, journeyUserAgent: String, journeyConfig: JourneyConfig): CallHandler3[String, String, JourneyConfig, Future[JourneyMongoDocument]] = 
      (mockJourneyRepository
        .set(_: String, _: String, _: JourneyConfig))
        .expects(journeyId, journeyUserAgent, journeyConfig)

    def get(journeyId: String): CallHandler1[String, Future[Option[JourneyMongoDocument]]] = 
      (mockJourneyRepository
        .get(_: String))
        .expects(journeyId)