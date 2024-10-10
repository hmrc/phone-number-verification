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

package uk.gov.hmrc.cipphonenumberverification.models

import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import play.api.libs.json.{Format, Json}

object internal {

  case class ValidatedPhoneNumber(phoneNumber: String, phoneNumberType: PhoneNumberType)

  object ValidatedPhoneNumber {

    object Implicits {
//      implicit val validatedPhoneNumberFormat: Format[ValidatedPhoneNumber] = Json.format[ValidatedPhoneNumber]

      implicit class ValidatedPhoneNumberSyntax(vpn: ValidatedPhoneNumber) {
        def isMobile: Boolean = vpn.phoneNumberType == PhoneNumberType.MOBILE
      }
    }
  }

  case class PhoneNumberVerificationCodeData(phoneNumber: String, verificationCode: String)

  object PhoneNumberVerificationCodeData {

    object Implicits {
      implicit val phoneNumberVerificationCodeDataFormat: Format[PhoneNumberVerificationCodeData] = Json.format[PhoneNumberVerificationCodeData]
    }
  }

  case class VerificationCodeNotificationRequest(phoneNumber: String, message: String)

  object VerificationCodeNotificationRequest {

    object Implicits {
      implicit val verificationCodeNotificationRequestFormat: Format[VerificationCodeNotificationRequest] = Json.format[VerificationCodeNotificationRequest]
    }
  }
}
