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

import akka.stream.Materializer
import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cipphonenumberverification.config.{AppConfig, CircuitBreakerConfig}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ValidateConnector @Inject()(httpClientV2: HttpClientV2, config: AppConfig)(implicit ec: ExecutionContext, val materializer: Materializer)
  extends Logging with CircuitBreakerWrapper {

  implicit val connectionFailure: Try[HttpResponse] => Boolean = {
    case Success(_) => false
    case Failure(_) => true
  }

  def callService(phoneNumber: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    withCircuitBreaker[HttpResponse](
      httpClientV2
        .post(url"${config.validationConfig.url}/customer-insight-platform/phone-number/validate")
        .withBody(Json.obj("phoneNumber" -> s"$phoneNumber"))
        .setHeader(("Authorization", config.validationConfig.authToken))
        .execute[HttpResponse]
    )
  }

  override def configCB: CircuitBreakerConfig = config.validationConfig.cbConfig
}
