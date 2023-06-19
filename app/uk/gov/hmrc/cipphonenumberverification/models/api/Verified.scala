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

package uk.gov.hmrc.cipphonenumberverification.models.api

import play.api.libs.json.{Format, Json}

sealed trait VerificationStatus {
  def code: StatusCode.StatusCode
  def message: String
}

case class Verified(override val code: StatusCode.StatusCode, override val message: String = "Verified") extends VerificationStatus
case class NotVerified(override val code: StatusCode.StatusCode, override val message: String) extends VerificationStatus
case class Indeterminate(override val code: StatusCode.StatusCode, override val message: String) extends VerificationStatus

object VerificationStatus {
  implicit val verifiedFormat: Format[Verified]           = Json.format[Verified]
  implicit val notVerifiedFormat: Format[NotVerified]     = Json.format[NotVerified]
  implicit val indeterminateFormat: Format[Indeterminate] = Json.format[Indeterminate]
}

object StatusCode extends Enumeration {
  type StatusCode = String

  val VERIFIED      = "Verified"
  val NOT_VERIFIED  = "Not verified"
  val INDETERMINATE = "Indeterminate"
}
