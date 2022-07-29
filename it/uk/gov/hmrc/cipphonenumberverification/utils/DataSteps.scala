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

package uk.gov.hmrc.cipphonenumberverification.utils

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberAndOtp
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

trait DataSteps {
  this: GuiceOneServerPerSuite =>

  private val repository = app.injector.instanceOf[PasscodeCacheRepository]

  val wsClient: WSClient = app.injector.instanceOf[WSClient]
  val baseUrl = s"http://localhost:$port"

  //mimics user reading text message
  def retrieveOtp(phoneNumber: String): Future[Option[PhoneNumberAndOtp]] = {
    repository.get[PhoneNumberAndOtp](phoneNumber)(DataKey("cip-phone-number-verification"))
  }

  def verify(phoneNumber: String): Future[WSResponse] = {
    wsClient
      .url(s"$baseUrl/customer-insight-platform/phone-number/verify")
      .post(Json.parse {
        s"""{"phoneNumber": "$phoneNumber"}""".stripMargin
      })
  }
}
