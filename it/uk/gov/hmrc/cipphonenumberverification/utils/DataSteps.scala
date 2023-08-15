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

package uk.gov.hmrc.cipphonenumberverification.utils

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndPasscode
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository

import scala.concurrent.Future

trait DataSteps {
  this: GuiceOneServerPerSuite =>

  private val repository = app.injector.instanceOf[PasscodeCacheRepository]

  val wsClient: WSClient = app.injector.instanceOf[WSClient]
  val baseUrl            = s"http://localhost:$port"

  //mimics user reading text message
  def retrievePasscode(phoneNumber: String): Future[Option[PhoneNumberAndPasscode]] =
    repository.get[PhoneNumberAndPasscode](phoneNumber)(PasscodeCacheRepository.phoneNumberPasscodeDataKey)

  def verify(phoneNumber: String): Future[WSResponse] =
    wsClient
      .url(s"$baseUrl/phone-number/verify")
      .withHttpHeaders(("Authorization", "local-test-token"))
      .post(Json.parse {
        s"""{"phoneNumber": "$phoneNumber"}""".stripMargin
      })
}
