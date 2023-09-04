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

package uk.gov.hmrc.cipphonenumberverification.config

import akka.actor.ActorSystem
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.inject.{AbstractModule, Provides}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment}
import uk.gov.hmrc.cipphonenumberverification.circuitbreaker.CircuitBreakerConfig
import uk.gov.hmrc.http.client.{HttpClientV2, HttpClientV2Impl}

import javax.inject.Named

class GuiceModule(environment: Environment, config: Configuration) extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[PhoneNumberUtil]).toInstance(PhoneNumberUtil.getInstance())

  @Provides
  @Named("internal-http-client")
  def provideInternalHttpClient(
    wsClient: WSClient,
    actorSystem: ActorSystem
  ): HttpClientV2 =
    new HttpClientV2Impl(wsClient, actorSystem, config, Seq())

  @Provides
  @Named("user-notifications-circuit-breaker")
  def provideCallValidateCircuitBreakerConfig: CircuitBreakerConfig = {
    val appName = config
      .getOptional[String]("appName")
      .getOrElse(throw new IllegalArgumentException("Cannot find appName in application.conf"))

    CircuitBreakerConfig(
      s"$appName-user-notifications",
      config.get[Int]("microservice.services.user-notifications.phone.circuit-breaker.maxFailures"),
      config.get[Int]("microservice.services.user-notifications.phone.circuit-breaker.resetTimeout"),
      config.get[Int]("microservice.services.user-notifications.phone.circuit-breaker.callTimeout")
    )
  }

}
