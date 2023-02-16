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

package uk.gov.hmrc.cipphonenumberverification.models.domain.data

import play.api.libs.functional.syntax.{toApplicativeOps, toFunctionalBuilderOps}
import play.api.libs.json.Reads.{maxLength, minLength}
import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class PhoneNumberAndPasscode(phoneNumber: String, passcode: String)

object PhoneNumberAndPasscode {
  val MIN_LENGTH_PASSCODE = 6
  val MAX_LENGTH_PASSCODE = 6

  implicit val reads: Reads[PhoneNumberAndPasscode] = ((JsPath \ "phoneNumber").read[String] and
    (JsPath \ "passcode").read[String](minLength[String](MIN_LENGTH_PASSCODE).keepAnd(maxLength[String](MAX_LENGTH_PASSCODE))))(PhoneNumberAndPasscode.apply _)

  implicit val writes: Writes[PhoneNumberAndPasscode] = Json.writes[PhoneNumberAndPasscode]
}
