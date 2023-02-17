/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsObject
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.api.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PasscodeServiceSpec extends AnyWordSpec with Matchers with IdiomaticMockito {

  "persistPasscode" should {
    "return passcode" in new SetUp {
      val phoneNumber                     = PhoneNumber("test")
      val passcode                        = "ABCDEF"
      val now                             = System.currentTimeMillis()
      val phoneNumberAndPasscodeToPersist = PhoneNumberPasscodeData(phoneNumber.phoneNumber, passcode = passcode, now)
      passcodeCacheRepositoryMock
        .put(phoneNumber.phoneNumber)(DataKey("cip-phone-number-verification"), phoneNumberAndPasscodeToPersist)
        .returns(Future.successful(CacheItem("", JsObject.empty, Instant.EPOCH, Instant.EPOCH)))

      val result = passcodeService.persistPasscode(phoneNumberAndPasscodeToPersist)

      await(result) shouldBe phoneNumberAndPasscodeToPersist
    }
  }

  "retrievePasscode" should {
    "return passcode" in new SetUp {
      val now        = System.currentTimeMillis()
      val dataFromDb = PhoneNumberPasscodeData("thePhoneNumber", "thePasscode", now)
      passcodeCacheRepositoryMock
        .get[PhoneNumberPasscodeData]("thePhoneNumber")(DataKey("cip-phone-number-verification"))
        .returns(Future.successful(Some(dataFromDb)))
      val result = passcodeService.retrievePasscode("thePhoneNumber")
      await(result) shouldBe Some(PhoneNumberPasscodeData("thePhoneNumber", "thePasscode", now))
    }
  }

  trait SetUp {
    val passcodeCacheRepositoryMock      = mock[PasscodeCacheRepository]
    val passcodeService: PasscodeService = new PasscodeService(passcodeCacheRepositoryMock)
  }
}
