/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.{ArgumentCaptor, IdiomaticMockito}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsObject
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cipphonenumberverification.models.{PhoneNumber, PhoneNumberAndOtp}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PasscodeServiceSpec extends AnyWordSpec
  with Matchers
  with IdiomaticMockito {

  "persistPasscode" should {
    //TODO: We should probably inject the OtpService into the Passcode service
    "return passcode" in new SetUp {
      val phoneNumber = PhoneNumber("test")
      val phoneNumberAndOtpCaptor = ArgumentCaptor.forClass(classOf[PhoneNumberAndOtp])
      passcodeCacheRepositoryMock.put(phoneNumber.phoneNumber)(DataKey("cip-phone-number-verification"), phoneNumberAndOtpCaptor.capture())
        .returns(Future.successful(CacheItem("", JsObject.empty, Instant.EPOCH, Instant.EPOCH)))
      val result = passcodeService.persistPasscode(phoneNumber.phoneNumber)
      val phoneNumberAndOtp = phoneNumberAndOtpCaptor.getValue()

      await(result) shouldBe phoneNumberAndOtp
    }
  }

  "retrievePasscode" should {
    "return passcode" in new SetUp {
      val phoneNumberAndOtp = PhoneNumberAndOtp("", "")
      passcodeCacheRepositoryMock.get[PhoneNumberAndOtp](phoneNumberAndOtp.phoneNumber)(DataKey("cip-phone-number-verification"))
        .returns(Future.successful(Some(PhoneNumberAndOtp("", ""))))
      val result = passcodeService.retrievePasscode(phoneNumberAndOtp.phoneNumber)
      await(result) shouldBe Some(PhoneNumberAndOtp("", ""))
    }
  }

  "deletePasscode" should {
    "return unit" in new SetUp {
      val passcode = PhoneNumberAndOtp("", "")
      passcodeCacheRepositoryMock.delete(passcode.phoneNumber)(DataKey("cip-phone-number-verification"))
        .returns(Future.unit)
      val result = passcodeService.deletePasscode(passcode)
      await(result) shouldBe()
    }
  }

  trait SetUp {
    val passcodeCacheRepositoryMock = mock[PasscodeCacheRepository]
    val passcodeService: PasscodeService = new PasscodeService(passcodeCacheRepositoryMock)
  }
}
