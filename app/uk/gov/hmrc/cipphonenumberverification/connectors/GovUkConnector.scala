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

package uk.gov.hmrc.cipphonenumberverification.connectors

import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.Passcode
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GovUkConnector @Inject()(httpClient: HttpClientV2, config: AppConfig)(implicit executionContext: ExecutionContext) {

  def notificationStatus(notificationId: String)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {

    httpClient
      .get(url"${config.govUkNotifyHost}/v2/notifications/$notificationId")
      .addHeaders((s"Authorization", s"Bearer $jwtBearerToken"))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  def sendPasscode(passcode: Passcode)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, HttpResponse]] = {

    // TODO Build this elsewhere
    val passcodeRequest = Json.obj(
      "phone_number" -> s"${passcode.phoneNumber}",
      "template_id" -> s"${config.templateId}",
      "personalisation" ->  Json.obj(
        "clientServiceName" -> "cip-phone-service",
        "passCode" -> s"${passcode.passcode}",
        "timeToLive" -> s"${config.cacheExpiry}")
    )

    httpClient
      .post(url"${config.govUkNotifyHost}/v2/notifications/sms")
      .addHeaders((s"Authorization", s"Bearer $jwtBearerToken"))
      .withBody(Json.toJson(passcodeRequest))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
  }

  private def jwtBearerToken = {
    val key = Keys.hmacShaKeyFor(config.govUkNotifyApiKeySecretKeyUuid.getBytes(StandardCharsets.UTF_8))

    val jwt = Jwts.builder()
      .setIssuer(config.govUkNotifyApiKeyIssUuid)
      .setIssuedAt(new Date())
      .signWith(SignatureAlgorithm.HS256, key)

    jwt.compact()
  }
}
