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

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cipphonenumberverification.models.internal.ValidatedPhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.INVALID_TELEPHONE_NUMBER
import uk.gov.hmrc.cipphonenumberverification.models.response.VerificationStatus

class ValidateServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "validate" should {
    "return success if telephone number is a valid UK mobile number" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("07890349087")
      result shouldBe Right[VerificationStatus, ValidatedPhoneNumber](ValidatedPhoneNumber("+447890349087", PhoneNumberType.MOBILE))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberValidated(any[String])
    }

    "return success if telephone number is a valid UK landline number" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("01292123456")
      result shouldBe Right[VerificationStatus, ValidatedPhoneNumber](ValidatedPhoneNumber("+441292123456", PhoneNumberType.FIXED_LINE))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberValidated(any[String])
    }

    "return success if telephone number is a valid non-UK number" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("+35312382300")
      result shouldBe Right[VerificationStatus, ValidatedPhoneNumber](ValidatedPhoneNumber("+35312382300", PhoneNumberType.FIXED_LINE))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberValidated(any[String])
    }

    "return failure if telephone number is empty" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("")
      result shouldBe Left[VerificationStatus, ValidatedPhoneNumber](VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberNotValidated()
    }

    "return failure if telephone number has no leading zero" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("7890349087")
      result shouldBe Left[VerificationStatus, ValidatedPhoneNumber](VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberNotValidated()
    }

    "return failure if telephone number contains letters" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("invalid")
      result shouldBe Left[VerificationStatus, ValidatedPhoneNumber](VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberNotValidated()
    }

    "return failure if telephone number fails to parse" in new SetUp {
      val result: Either[VerificationStatus, ValidatedPhoneNumber] = validateService.validate("xxxxxxxxxx")
      result shouldBe Left[VerificationStatus, ValidatedPhoneNumber](VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))
      verify(mockMetricsServer, atLeastOnce()).recordPhoneNumberNotValidated()
    }
  }

  trait SetUp {
    lazy val mockMetricsServer: MetricsService = mock[MetricsService]
    val validateService                        = new ValidateService(PhoneNumberUtil.getInstance(), mockMetricsServer)
  }
}
