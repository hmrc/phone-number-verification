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
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.ahc.AhcCurlRequestLogger
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cipphonenumberverification.config.{AppConfig, CircuitBreakerConfig}
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class GovUkConnector @Inject()(httpClient: HttpClientV2, config: AppConfig)
                              (implicit executionContext: ExecutionContext, protected val materializer: Materializer)
  extends Logging with CircuitBreakerWrapper {

  implicit val connectionFailure: Try[Either[UpstreamErrorResponse, HttpResponse]] => Boolean = {
    case Success(_) => false
    case Failure(_) => true
  }

  def notificationStatus(notificationId: String)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    withCircuitBreaker[Either[UpstreamErrorResponse, HttpResponse]](
      httpClient
        .get(url"${config.govNotifyConfig.url}/v2/notifications/$notificationId")
        .setHeader((s"Authorization", s"Bearer $jwtBearerToken"))
        .withProxy
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
  }

  def sendPasscode(phoneNumberPasscodeData: PhoneNumberPasscodeData)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {
    // TODO Build this elsewhere
    val passcodeRequest = Json.obj(
      "phone_number" -> s"${phoneNumberPasscodeData.phoneNumber}",
      "template_id" -> s"${config.govNotifyConfig.templateId}",
      "personalisation" -> Json.obj(
        "clientServiceName" -> "cip-phone-service",
        "passcode" -> s"${phoneNumberPasscodeData.passcode}",
        "timeToLive" -> s"${config.passcodeExpiry}")
    )

    withCircuitBreaker[Either[UpstreamErrorResponse, HttpResponse]](
      httpClient
        .post(url"${config.govNotifyConfig.url}/v2/notifications/sms")
        .setHeader((s"Authorization", s"Bearer $jwtBearerToken"))
        .transform(_.withRequestFilter(AhcCurlRequestLogger()))
        .withBody(Json.toJson(passcodeRequest))
        .withProxy
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
  }

  private def jwtBearerToken = {
    val key = Keys.hmacShaKeyFor(config.govNotifyConfig.govUkNotifyApiKeySecretKeyUuid.getBytes(StandardCharsets.UTF_8))

    val jwt = Jwts.builder()
      .setIssuer(config.govNotifyConfig.govUkNotifyApiKeyIssUuid)
      .setIssuedAt(new Date())
      .signWith(SignatureAlgorithm.HS256, key)

    jwt.compact()
  }

  override def configCB: CircuitBreakerConfig = config.govNotifyConfig.cbConfig
}
