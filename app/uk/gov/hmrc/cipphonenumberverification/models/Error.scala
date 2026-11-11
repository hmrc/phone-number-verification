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

import play.api.libs.json.{Json, Writes}

sealed class Error(val code: String, val desc: String)

case class AccessForbidden(userAgent: String, formUrl: String)
    extends Error(
      "USER_NOT_ALLOWED",
      s"user agent $userAgent is not authorized to use this service. Please complete '$formUrl' to request access."
    )

object Error {

  implicit val writes: Writes[Error] = Writes {
    model =>
      Json.obj(
        "statusCode" -> model.code,
        "message"    -> model.desc
      )
  }
}
