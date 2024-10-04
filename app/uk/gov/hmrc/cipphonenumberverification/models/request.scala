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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{maxLength, minLength}
import play.api.libs.json._

object request {
  case class PhoneNumber(phoneNumber: String)

  object PhoneNumber {

    object Implicits {
      val MIN_LENGTH = 7
      val MAX_LENGTH = 20

      implicit val phoneNumberReads: Reads[PhoneNumber] =
        (JsPath \ "phoneNumber")
          .read[String](
            minLength[String](MIN_LENGTH)
              .keepAnd(maxLength[String](MAX_LENGTH))
          )
          .map(PhoneNumber.apply)
      implicit val phoneNumberWrites: Writes[PhoneNumber] = Json.writes[PhoneNumber]
    }
  }

  case class PhoneNumberAndVerificationCode(phoneNumber: String, verificationCode: String)

  object PhoneNumberAndVerificationCode {

    object Implicits {
      val MIN_LENGTH_PASSCODE = 6
      val MAX_LENGTH_PASSCODE = 6

      implicit val reads: Reads[PhoneNumberAndVerificationCode] = ((JsPath \ "phoneNumber").read[String] and
        (JsPath \ "verificationCode").read[String](minLength[String](MIN_LENGTH_PASSCODE).keepAnd(maxLength[String](MAX_LENGTH_PASSCODE))))(
        PhoneNumberAndVerificationCode.apply _
      )

      implicit val writes: Writes[PhoneNumberAndVerificationCode] = Json.writes[PhoneNumberAndVerificationCode]
    }
  }

  object testOnly {
    case class PhoneNumber(phoneNumber: String)

    object PhoneNumber {
      implicit val phoneNumberFormat: Format[PhoneNumber] = Json.format[PhoneNumber]
    }
  }

}
