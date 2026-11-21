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

package uk.gov.hmrc.cipphonenumberverification.testOnly.repositories

import org.scalatest.matchers.should.Matchers.shouldBe
import uk.gov.hmrc.cipphonenumberverification.BaseISpec
import uk.gov.hmrc.cipphonenumberverification.config.MockAppConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.PhoneNumberAttemptsMongoRepository
import uk.gov.hmrc.cipphonenumberverification.repositories.models.PhoneNumberAttemptsMongoDocument
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class TestPhoneNumberAttemptsRepositoryISpec
  extends BaseISpec
    with DefaultPlayMongoRepositorySupport[PhoneNumberAttemptsMongoDocument]
    with MockAppConfig:

  protected override val repository: PhoneNumberAttemptsMongoRepository = {
    MockAppConfig.phoneNumberAttemptsExpiry(24)

    new PhoneNumberAttemptsMongoRepository(
      mongoComponent,
      mockAppConfig
    )
  }

  private lazy val testRepository = new TestPhoneNumberAttemptsRepository(repository)

  private val credentialId = "testCredentialId"

  "The TestPhoneNumberAttemptsRepository" should :

    "Delete all attempts for a credentialId only" in:
      repository.recordFailedAttempt(credentialId, "07123456789").futureValue
      repository.recordFailedAttempt(credentialId, "07987654321").futureValue
      repository.recordFailedAttempt("otherCredentialId", "07123456789").futureValue

      testRepository.deleteAttempts(credentialId).futureValue shouldBe 2L

      repository.getAttempts(credentialId).futureValue shouldBe Seq.empty
      repository.getAttempts("otherCredentialId").futureValue.size shouldBe 1

    "Return zero when no attempts exist for a credentialId" in:
      testRepository.deleteAttempts("unknown").futureValue shouldBe 0L
