/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.cipphonenumberverification.models.ErrorResponse.Codes.Code

case class ErrorResponse(code: Code, message: String)

object ErrorResponse {
  implicit val writes: OWrites[ErrorResponse] = Json.writes[ErrorResponse]

  object Codes extends Enumeration {
    type Code = Value

    val VALIDATION_ERROR, NOT_FOUND, EXTERNAL_SERVICE_FAIL, PASSCODE_PERSISTING_FAIL, PASSCODE_VERIFY_FAIL = Value
  }
}

