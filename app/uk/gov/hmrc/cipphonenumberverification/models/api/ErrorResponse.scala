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

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Message.Message

case class ErrorResponse(code: Int, message: Message)

object ErrorResponse {
  implicit val writes: OWrites[ErrorResponse] = Json.writes[ErrorResponse]

  object Codes extends Enumeration {
    type Code = Value

    val NOTIFICATION_NOT_FOUND   = Value(1001)
    val VALIDATION_ERROR         = Value(1002)
    val VERIFICATION_ERROR       = Value(1003)
    val EXTERNAL_API_FAIL        = Value(1004)
    val PASSCODE_VERIFY_FAIL     = Value(1005)
    val EXTERNAL_SERVICE_FAIL    = Value(1006)
    val MESSAGE_THROTTLED_OUT    = Value(1007)
    val PASSCODE_PERSISTING_FAIL = Value(1008)
    val EXTERNAL_SERVICE_TIMEOUT = Value(1009)
  }

  object Message extends Enumeration {
    type Message = String

    val SERVER_CURRENTLY_UNAVAILABLE          = "Server currently unavailable"
    val SERVER_EXPERIENCED_AN_ISSUE           = "Server has experienced an issue"
    val EXTERNAL_SERVER_CURRENTLY_UNAVAILABLE = "External server currently unavailable"
    val PASSCODE_ALLOWED_TIME_ELAPSED         = "The passcode has expired. Request a new passcode"
    val PASSCODE_STORED_TIME_ELAPSED          = "Enter a correct passcode"
    val EXTERNAL_SERVER_TIMEOUT               = "External server timeout"
  }
}
