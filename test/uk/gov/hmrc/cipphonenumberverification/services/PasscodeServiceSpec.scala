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

import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsObject
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipphonenumberverification.models.internal.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.CacheItem

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PasscodeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import PhoneNumberPasscodeData.Implicits._

  "persistPasscode" should {
    "return passcode" in new SetUp {
      val phoneNumber: PhoneNumber                                 = PhoneNumber("test")
      val passcode                                                 = "ABCDEF"
      val phoneNumberAndPasscodeToPersist: PhoneNumberPasscodeData = PhoneNumberPasscodeData(phoneNumber.phoneNumber, passcode = passcode)
      when(
        passcodeCacheRepositoryMock
          .put(phoneNumber.phoneNumber)(PasscodeCacheRepository.phoneNumberPasscodeDataDataKey, phoneNumberAndPasscodeToPersist)
      )
        .thenReturn(Future.successful(CacheItem("", JsObject.empty, Instant.EPOCH, Instant.EPOCH)))

      val result: Future[PhoneNumberPasscodeData] = passcodeService.persistPasscode(phoneNumberAndPasscodeToPersist)

      await(result) shouldBe phoneNumberAndPasscodeToPersist
    }
  }

  "retrievePasscode" should {
    "return passcode" in new SetUp {
      val dataFromDb: PhoneNumberPasscodeData = PhoneNumberPasscodeData("thePhoneNumber", "thePasscode")
      when(
        passcodeCacheRepositoryMock
          .get[PhoneNumberPasscodeData]("thePhoneNumber")(PasscodeCacheRepository.phoneNumberPasscodeDataDataKey)
      )
        .thenReturn(Future.successful(Some(dataFromDb)))
      val result: Future[Option[PhoneNumberPasscodeData]] = passcodeService.retrievePasscode("thePhoneNumber")
      await(result) shouldBe Some(PhoneNumberPasscodeData("thePhoneNumber", "thePasscode"))
    }
  }

  trait SetUp {
    val passcodeCacheRepositoryMock: PasscodeCacheRepository = mock[PasscodeCacheRepository]
    val passcodeService: PasscodeService                     = new PasscodeService(passcodeCacheRepositoryMock)
  }
}
