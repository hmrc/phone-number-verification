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
import uk.gov.hmrc.cipphonenumberverification.BaseISpec
import uk.gov.hmrc.cipphonenumberverification.config.MockAppConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.models.PhoneNumberAttemptsMongoDocument
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class PhoneNumberAttemptsMongoRepositoryISpec
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

  private val credentialId = "testCredentialId"

  "The PhoneNumberAttemptsMongoRepository" should :

    "Create a document on the first failed attempt" in:
      val result = repository.recordFailedAttempt(credentialId, "07123456789").futureValue

      result.credentialId shouldBe credentialId
      result.phoneNumber shouldBe "07123456789"
      result.failedAttempts shouldBe 1
      result.createdAt shouldBe result.lastFailedAttemptAt

    "Increment failed attempts for the same credentialId and phone number without changing createdAt" in:
      val first = repository.recordFailedAttempt(credentialId, "07123456789").futureValue
      repository.recordFailedAttempt(credentialId, "07123456789").futureValue
      val third = repository.recordFailedAttempt(credentialId, "07123456789").futureValue

      third.failedAttempts shouldBe 3
      third.createdAt shouldBe first.createdAt

    "Return attempts for each phone number tried by a credentialId only" in:
      repository.recordFailedAttempt(credentialId, "07123456789").futureValue
      repository.recordFailedAttempt(credentialId, "07123456789").futureValue
      repository.recordFailedAttempt(credentialId, "07987654321").futureValue
      repository.recordFailedAttempt("otherCredentialId", "07123456789").futureValue

      val result = repository.getAttempts(credentialId).futureValue

      result.map(a => a.phoneNumber -> a.failedAttempts).toMap shouldBe Map("07123456789" -> 2, "07987654321" -> 1)

    "Return no attempts for an unknown credentialId" in:
      repository.getAttempts("unknown").futureValue shouldBe Seq.empty
