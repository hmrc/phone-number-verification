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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.StatusCode

object audit {
  abstract class AuditEvent(phoneNumber: String, passcode: String)

  object AuditType extends Enumeration {
    type AuditType = Value

    val PhoneNumberVerificationRequest: AuditType = Value
    val PhoneNumberVerificationCheck: AuditType   = Value
  }

  case class VerificationCheckAuditEvent(phoneNumber: String, passcode: String, result: StatusCode) extends audit.AuditEvent(phoneNumber, passcode)

  object VerificationCheckAuditEvent {

    object Implicits {
      implicit val verificationCheckAuditEventFormat: Format[VerificationCheckAuditEvent] = Json.format[VerificationCheckAuditEvent]
    }
  }

  case class VerificationRequestAuditEvent(phoneNumber: String, verificationCode: String) extends audit.AuditEvent(phoneNumber, verificationCode)

  object VerificationRequestAuditEvent {

    object Implicits {
      implicit val verificationRequestAuditEventFormat: Format[VerificationRequestAuditEvent] = Json.format[VerificationRequestAuditEvent]
    }
  }
}
