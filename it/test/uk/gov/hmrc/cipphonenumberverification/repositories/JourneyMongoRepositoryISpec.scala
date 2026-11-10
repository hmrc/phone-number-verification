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

package uk.gov.hmrc.cipphonenumberverification.repositories

import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cipphonenumberverification.BaseISpec
import uk.gov.hmrc.cipphonenumberverification.config.MockAppConfig
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.models.JourneyMongoDocument
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class JourneyMongoRepositoryISpec
  extends BaseISpec
    with DefaultPlayMongoRepositorySupport[JourneyMongoDocument]
    with MockAppConfig:

  protected override val repository: JourneyMongoRepository = {
    MockAppConfig.journeyCacheExpiry(15)

    new JourneyMongoRepository(
      mongoComponent,
      mockAppConfig
    )
  }

  "The JourneyMongoRepository" should :

    "Store and retrieve a journey config" in:

      val journeyId: String = "testJourneyId"
      val journeyConfig: JourneyConfig = testJourneyConfigWithPhoneNumber
      val journeyUserAgent: String = testUserAgent

      repository.set(journeyId, journeyUserAgent, journeyConfig).futureValue

      val result = repository.get(journeyId).futureValue
      val journeyMongoDocument = result.get

      journeyMongoDocument.journeyId shouldBe journeyId
      journeyMongoDocument.journeyUserAgent shouldBe journeyUserAgent
      journeyMongoDocument.journeyConfig shouldBe journeyConfig