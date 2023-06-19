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

package uk.gov.hmrc.cipphonenumberverification.service

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.mockito.ArgumentMatchersSugar.any
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.api.{ErrorResponse, ValidatedPhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.services.ValidateService

class ValidateServiceSpec extends AnyWordSpec with Matchers with IdiomaticMockito {

  "validate" should {
    "return success if telephone number is a valid UK mobile number" in new SetUp {
      val result = validateService.validate("07890349087")
      result shouldBe Right[ErrorResponse, ValidatedPhoneNumber](ValidatedPhoneNumber("+447890349087", "Mobile"))
      mockMetricsServer.recordMetric(any[String]) was called
    }

    "return success if telephone number is a valid UK landline number" in new SetUp {
      val result = validateService.validate("01292123456")
      result shouldBe Right[ErrorResponse, ValidatedPhoneNumber](ValidatedPhoneNumber("+441292123456", "Fixed_line"))
      mockMetricsServer.recordMetric(any[String]) was called
    }

    "return success if telephone number is a valid non-UK number" in new SetUp {
      val result = validateService.validate("+35312382300")
      result shouldBe Right[ErrorResponse, ValidatedPhoneNumber](ValidatedPhoneNumber("+35312382300", "Fixed_line"))
      mockMetricsServer.recordMetric(any[String]) was called
    }

    "return failure if telephone number is empty" in new SetUp {
      val result = validateService.validate("")
      result shouldBe Left[ErrorResponse, ValidatedPhoneNumber](ErrorResponse(1002, "Enter a valid telephone number"))
      mockMetricsServer.recordMetric(any[String]) was called
    }

    "return failure if telephone number has no leading zero" in new SetUp {
      val result = validateService.validate("7890349087")
      result shouldBe Left[ErrorResponse, ValidatedPhoneNumber](ErrorResponse(1002, "Enter a valid telephone number"))
      mockMetricsServer.recordMetric(any[String]) was called
    }

    "return failure if telephone number contains letters" in new SetUp {
      val result = validateService.validate("invalid")
      result shouldBe Left[ErrorResponse, ValidatedPhoneNumber](ErrorResponse(1002, "Enter a valid telephone number"))
      mockMetricsServer.recordMetric(any[String]) was called
    }

    "return failure if telephone number fails to parse" in new SetUp {
      val result = validateService.validate("xxxxxxxxxx")
      result shouldBe Left[ErrorResponse, ValidatedPhoneNumber](ErrorResponse(1002, "Enter a valid telephone number"))
      mockMetricsServer.recordMetric(any[String]) was called
    }
  }

  trait SetUp {
    lazy val mockMetricsServer = mock[MetricsService]
    val validateService        = new ValidateService(PhoneNumberUtil.getInstance(), mockMetricsServer)
  }
}
