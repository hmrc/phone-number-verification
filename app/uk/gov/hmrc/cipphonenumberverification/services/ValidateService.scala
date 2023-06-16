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
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.Phonenumber.{PhoneNumber => GPhoneNumber}
import org.apache.commons.lang3.StringUtils
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, Ok}
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Message.INVALID_TELEPHONE_NUMBER
import uk.gov.hmrc.cipphonenumberverification.models.api.{ErrorResponse}
import uk.gov.hmrc.cipphonenumberverification.models.http.validation.PhoneNumberResponse

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton()
class ValidateService @Inject() (phoneNumberUtil: PhoneNumberUtil, metricsService: MetricsService) extends Logging {

  private val formatInE164 = (x: GPhoneNumber) => phoneNumberUtil.format(x, PhoneNumberFormat.E164)

  def validate(phoneNumber: String)(implicit defaultRegion: String = "GB"): Future[Result] =
    Try {
      val mandatoryFirstChars = "+0"
      phoneNumber match {
        case _
            if phoneNumber.isEmpty || existsLetter(phoneNumber) || containsChars(phoneNumber) ||
              !mandatoryFirstChars.contains(phoneNumber.charAt(0)) =>
          false
        case _ if isValidPhoneNumber(phoneNumber) =>
          val telephoneNumberType = getPhoneNumberType(phoneNumber).name.toLowerCase
          metricsService.recordMetric(s"${telephoneNumberType}_validation_count")
          PhoneNumberResponse(formatInE164(parsePhoneNumber(phoneNumber)), telephoneNumberType.capitalize)
      }
    } match {
      case Success(phoneNumberResponse: PhoneNumberResponse) => Future.successful(Ok(Json.toJson(phoneNumberResponse)))
      case Success(false) | Failure(_) =>
        metricsService.recordMetric("telephone_number_validation_failure")
        logger.warn("Failed to validate phone number")
        Future.successful(BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR.id, INVALID_TELEPHONE_NUMBER))))
    }

  private def isValidPhoneNumber(phoneNumber: String)(implicit defaultRegion: String) = phoneNumberUtil.isValidNumber(parsePhoneNumber(phoneNumber))

  private def getPhoneNumberType(phoneNumber: String)(implicit defaultRegion: String) =
    phoneNumberUtil.getNumberType(phoneNumberUtil.parse(phoneNumber, defaultRegion))

  private def existsLetter(phoneNumber: String) = phoneNumber.exists(_.isLetter)

  private def containsChars(phoneNumber: String) = StringUtils.containsAny(phoneNumber, "[]")

  private def parsePhoneNumber(phoneNumber: String)(implicit defaultRegion: String): GPhoneNumber = phoneNumberUtil.parse(phoneNumber, defaultRegion)
}
