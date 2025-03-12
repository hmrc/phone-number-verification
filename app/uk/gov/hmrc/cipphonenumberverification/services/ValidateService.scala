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
import com.google.i18n.phonenumbers.PhoneNumberUtil.{PhoneNumberFormat, PhoneNumberType => GPhoneNumberType}
import com.google.i18n.phonenumbers.Phonenumber.{PhoneNumber => GPhoneNumber}
import play.api.Logging
import uk.gov.hmrc.cipphonenumberverification.models.internal.ValidatedPhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models.response.{StatusCode, StatusMessage, VerificationStatus}

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton()
class ValidateService @Inject() (phoneNumberUtil: PhoneNumberUtil, metricsService: MetricsService) extends Logging {

  def validate(phoneNumber: String, defaultRegion: String = "GB"): Either[VerificationStatus, ValidatedPhoneNumber] =
    Try {
      val maybePhoneNumber = isPhoneNumberValid(phoneNumber, defaultRegion)
      if (maybePhoneNumber.isDefined) {
        val phoneNumberType = maybePhoneNumber.get._2
        metricsService.recordPhoneNumberValidated(phoneNumberType.toString)
        Some(ValidatedPhoneNumber(formatInE164(maybePhoneNumber.get._1), phoneNumberType))
      } else {
        None
      }
    } match {
      case Success(phoneNumberResponse: Some[ValidatedPhoneNumber]) =>
        Right(phoneNumberResponse.get)
      case Success(None) | Failure(_) =>
        metricsService.recordPhoneNumberNotValidated()
        logger.warn("Failed to validate phone number")
        Left(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER))
    }

  private def isPhoneNumberValid(phoneNumber: String, defaultRegion: String): Option[(GPhoneNumber, GPhoneNumberType)] =
    if (phoneNumberUtil.isValidNumber(parsePhoneNumber(phoneNumber, defaultRegion))) {
      Try {
        val pn  = parsePhoneNumber(phoneNumber, defaultRegion)
        val pnt = phoneNumberUtil.getNumberType(pn)
        (pn, pnt)
      }.toOption
    } else {
      None
    }

  private val formatInE164 = (x: GPhoneNumber) => phoneNumberUtil.format(x, PhoneNumberFormat.E164)

  private def parsePhoneNumber(phoneNumber: String, defaultRegion: String): GPhoneNumber =
    phoneNumberUtil.parse(phoneNumber, defaultRegion)
}
