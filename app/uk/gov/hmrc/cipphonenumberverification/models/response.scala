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

import play.api.libs.json._

object response {

  case class VerificationStatus(status: StatusCode.StatusCode, message: StatusMessage.StatusMessage)

  object VerificationStatus {
    implicit val verificationStatusFormat: Format[VerificationStatus] = Json.format[VerificationStatus]
  }

  object StatusCode extends Enumeration {
    type StatusCode = Value

    val VALIDATION_ERROR: StatusCode = Value("VALIDATION_ERROR")

    val CODE_SENT: StatusCode       = Value("CODE_SENT")
    val CODE_NOT_SENT: StatusCode   = Value("CODE_NOT_SENT")
    val CODE_SEND_ERROR: StatusCode = Value("CODE_SEND_ERROR")

    val CODE_VERIFY_FAILURE: StatusCode = Value("CODE_VERIFY_FAILURE")
    val CODE_VERIFIED: StatusCode       = Value("CODE_VERIFIFIED")

    val CODE_PERSISTING_FAIL: StatusCode = Value("CODE_PERSISTING_FAIL")

    val MESSAGE_THROTTLED_OUT: StatusCode = Value("MESSAGE_THROTTLED_OUT")
    val EXTERNAL_SERVICE_FAIL: StatusCode = Value("EXTERNAL_SERVICE_FAIL")
    val EXTERNAL_API_FAIL: StatusCode     = Value("EXTERNAL_API_FAIL")

    val INDETERMINATE: StatusCode = Value("INDETERMINATE")

    implicit val statusCodeFormat: Format[StatusCode.StatusCode] = Json.formatEnum(this)
  }

  object StatusMessage extends Enumeration {
    type StatusMessage = Value

    val CODE_SENT: StatusMessage               = Value("Phone verification code successfully sent")
    val CODE_NOT_SENT: StatusMessage           = Value("Could not send phone verification code")
    val ONLY_MOBILES_VERIFIABLE: StatusMessage = Value("Only mobile numbers can be verified")

    val INVALID_TELEPHONE_NUMBER: StatusMessage             = Value("Enter a valid telephone number")
    val INVALID_TELEPHONE_NUMBER_OR_PASSCODE: StatusMessage = Value("Enter a valid telephone number/passcode")

    val CODE_VERIFIED: StatusMessage       = Value("Passcode successfully verified")
    val CODE_NOT_RECOGNISED: StatusMessage = Value("Enter a valid passcode")

    val SERVICE_THROTTLED_ERROR: StatusMessage = Value("The request for the API is throttled as you have exceeded your quota")

    val SERVER_CURRENTLY_UNAVAILABLE: StatusMessage          = Value("Server currently unavailable")
    val SERVER_EXPERIENCED_AN_ISSUE: StatusMessage           = Value("Server has experienced an issue")
    val EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE: StatusMessage = Value("External server currently unavailable")

    implicit val statusMessageFormat: Format[StatusMessage.StatusMessage] = Json.formatEnum(this)
  }
}
