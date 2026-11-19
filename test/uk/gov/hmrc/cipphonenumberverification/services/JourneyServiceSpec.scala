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

import org.scalatest.matchers.should.Matchers.{shouldBe, shouldNot}
import uk.gov.hmrc.cipphonenumberverification.connectors.MockJourneyRepository
import uk.gov.hmrc.cipphonenumberverification.models.Audit.AuditType
import uk.gov.hmrc.cipphonenumberverification.repositories.models.JourneyMongoDocument
import uk.gov.hmrc.cipphonenumberverification.{BaseSpec, TestData}
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.Future

class JourneyServiceSpec
  extends BaseSpec
    with MockJourneyRepository
    with MockAuditService
    with TestData:

  trait SetUp {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val service: JourneyService = new JourneyService(mockJourneyRepository, mockAuditService) {
      override def generateJourneyId: String = testJourneyId
    }
  }

  "generateJourneyId" should :

    "return a valid UUID string" in:
      val service = new JourneyService(mockJourneyRepository, mockAuditService)

      val result: String = service.generateJourneyId

      UUID.fromString(result) shouldBe a[UUID]

    "generate a different journey ID for each call" in:
      val service = new JourneyService(mockJourneyRepository, mockAuditService)

      val firstResult: String = service.generateJourneyId
      val secondResult: String = service.generateJourneyId

      firstResult shouldNot be(secondResult)

  "initJourney" should :

    "fail when persisting the journey fails" in new SetUp:
      MockJourneyRepository
        .set(testJourneyId, testUserAgent, testJourneyConfigWithNoPhoneNumber)
        .returns(Future.failed(new RuntimeException("database failure")))

      val result: Future[String] = service.initJourney(testUserAgent, testJourneyConfigWithNoPhoneNumber)

      val exception: Throwable = result.failed.futureValue
      exception shouldBe a[RuntimeException]
      exception.getMessage shouldBe "database failure"

    "audit and return the correct redirect URL when phoneNumberToVerify is empty" in new SetUp:

      MockJourneyRepository
        .set(testJourneyId, testUserAgent, testJourneyConfigWithNoPhoneNumber)
        .returns(Future.successful(testJourneyMongoDocument(testJourneyConfigWithNoPhoneNumber)))

      MockAuditService.auditJourneyInitialised(testJourneyId, testUserAgent, testJourneyConfigWithNoPhoneNumber)

      val result: String = service.initJourney(testUserAgent, testJourneyConfigWithNoPhoneNumber).futureValue

      result shouldBe s"/phone-number-verification/journey/$testJourneyId/enter-phone-number"

    "audit and return the correct redirect URL when phoneNumberToVerify is not empty" in new SetUp:
      MockJourneyRepository
        .set(testJourneyId, testUserAgent, testJourneyConfigWithPhoneNumber)
        .returns(Future.successful(testJourneyMongoDocument(testJourneyConfigWithPhoneNumber)))

      MockAuditService.auditJourneyInitialised(testJourneyId, testUserAgent, testJourneyConfigWithPhoneNumber)

      val result: String = service.initJourney(testUserAgent, testJourneyConfigWithPhoneNumber).futureValue

      result shouldBe s"/phone-number-verification/journey/$testJourneyId/enter-pass-code"

    "getJourneyDetails" should :

      "return the journey config when it exists" in new SetUp:

        val journeyResponseFromMongo: JourneyMongoDocument = testJourneyMongoDocument(testJourneyConfigWithNoPhoneNumber)

        MockJourneyRepository
          .get(testJourneyId)
          .returns(Future.successful(Some(journeyResponseFromMongo)))

        val result: Option[JourneyMongoDocument] = service.getJourneyDetails(testJourneyId).futureValue

        result shouldBe Some(journeyResponseFromMongo)

      "return None when the journey config does not exist" in new SetUp:
        MockJourneyRepository
          .get(testJourneyId)
          .returns(Future.successful(None))

        val result: Option[JourneyMongoDocument] = service.getJourneyDetails(testJourneyId).futureValue

        result shouldBe None
