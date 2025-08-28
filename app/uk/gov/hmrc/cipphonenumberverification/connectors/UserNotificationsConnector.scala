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

package uk.gov.hmrc.cipphonenumberverification.connectors

import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.internal.{PhoneNumberVerificationCodeData, VerificationCodeNotificationRequest}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserNotificationsConnector @Inject() (httpClient: HttpClientV2, config: AppConfig)(implicit ec: ExecutionContext) {

  import VerificationCodeNotificationRequest.Implicits._

  def sendVerificationCode(
    phoneNumberVerificationCodeData: PhoneNumberVerificationCodeData
  )(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {

    val message                             = s"Your phone verification code is: ${phoneNumberVerificationCodeData.verificationCode}"
    val verificationCodeNotificationRequest = VerificationCodeNotificationRequest(phoneNumberVerificationCodeData.phoneNumber, message)

    httpClient
      .post(url"${config.phoneNotificationConfig.url}/notifications/sms")
      .setHeader(HeaderNames.AUTHORIZATION -> config.phoneNotificationAuthHeader)
      .withBody(Json.toJson(verificationCodeNotificationRequest))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }
}
