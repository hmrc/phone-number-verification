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
import uk.gov.hmrc.cipphonenumberverification.models.internal.PhoneNumberVerificationCodeData
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.repositories.VerificationCodeCacheRepository
import uk.gov.hmrc.mongo.cache.CacheItem

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VerificationCodeServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {
  import PhoneNumberVerificationCodeData.Implicits._

  "persistVerificationCode" should {
    "return verificationCode" in new SetUp {
      val phoneNumber: PhoneNumber = PhoneNumber("test")
      val verificationCode         = "ABCDEF"
      val phoneNumberAndVerificationCodeToPersist: PhoneNumberVerificationCodeData =
        PhoneNumberVerificationCodeData(phoneNumber.phoneNumber, verificationCode = verificationCode)
      when(
        verificationCodeCacheRepositoryMock
          .put(phoneNumber.phoneNumber)(VerificationCodeCacheRepository.phoneNumberVerificationCodeDataDataKey, phoneNumberAndVerificationCodeToPersist)
      )
        .thenReturn(Future.successful(CacheItem("", JsObject.empty, Instant.EPOCH, Instant.EPOCH)))

      val result: Future[PhoneNumberVerificationCodeData] = verificationCodeService.persistVerificationCode(phoneNumberAndVerificationCodeToPersist)

      await(result) shouldBe phoneNumberAndVerificationCodeToPersist
    }
  }

  "retrieveVerificationCode" should {
    "return verificationCode" in new SetUp {
      val dataFromDb: PhoneNumberVerificationCodeData = PhoneNumberVerificationCodeData("thePhoneNumber", "theVerificationCode")
      when(
        verificationCodeCacheRepositoryMock
          .get[PhoneNumberVerificationCodeData]("thePhoneNumber")(VerificationCodeCacheRepository.phoneNumberVerificationCodeDataDataKey)
      )
        .thenReturn(Future.successful(Some(dataFromDb)))
      val result: Future[Option[PhoneNumberVerificationCodeData]] = verificationCodeService.retrieveVerificationCode("thePhoneNumber")
      await(result) shouldBe Some(PhoneNumberVerificationCodeData("thePhoneNumber", "theVerificationCode"))
    }
  }

  trait SetUp {
    val verificationCodeCacheRepositoryMock: VerificationCodeCacheRepository = mock[VerificationCodeCacheRepository]
    val verificationCodeService: VerificationCodeService                     = new VerificationCodeService(verificationCodeCacheRepositoryMock)
  }
}
