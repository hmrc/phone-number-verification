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

import akka.stream.Materializer
import play.api.Logging
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cipphonenumberverification.config.{AppConfig, CircuitBreakerConfig}
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PasscodeNotificationRequest
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class UserNotificationsConnector @Inject() (httpClient: HttpClientV2, config: AppConfig)(implicit
  executionContext: ExecutionContext,
  protected val materializer: Materializer
) extends Logging
    with CircuitBreakerWrapper {

  implicit val connectionFailure: Try[Either[UpstreamErrorResponse, HttpResponse]] => Boolean = {
    case Success(_) => false
    case Failure(_) => true
  }

  def sendPasscode(phoneNumberPasscodeData: PhoneNumberPasscodeData)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {

    val message                     = s"Your Phone verification code: ${phoneNumberPasscodeData.passcode}"
    val passcodeNotificationRequest = PasscodeNotificationRequest(phoneNumberPasscodeData.phoneNumber, message)

    withCircuitBreaker[Either[UpstreamErrorResponse, HttpResponse]](
      httpClient
        .post(url"${config.phoneNotificationConfig.url}/notifications/sms")
        .setHeader(HeaderNames.AUTHORIZATION -> config.phoneNotificationAuthHeader)
        .withBody(Json.toJson(passcodeNotificationRequest))
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
  }

  override def configCB: CircuitBreakerConfig = config.phoneNotificationConfig.cbConfig
}
