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

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.inject.{AbstractModule, Provides}
import org.apache.pekko.actor.ActorSystem
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment}
import uk.gov.hmrc.cipphonenumberverification.connectors.UserNotificationsConnector
import uk.gov.hmrc.cipphonenumberverification.services._
import uk.gov.hmrc.http.client.{HttpClientV2, HttpClientV2Impl}

import javax.inject.Named
import scala.concurrent.ExecutionContext

class GuiceModule(environment: Environment, config: Configuration) extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[PhoneNumberUtil]).toInstance(PhoneNumberUtil.getInstance())

  @Provides
  def provideSendCodeService(appConfig: AppConfig,
                             verificationCodeGenerator: VerificationCodeGenerator,
                             auditService: AuditService,
                             verificationCodeService: VerificationCodeService,
                             validateService: ValidateService,
                             userNotificationsConnector: UserNotificationsConnector,
                             metricsService: MetricsService
  )(implicit
    ec: ExecutionContext
  ): SendCodeService =
    if (appConfig.useTestSendCodeService) {
      new TestSendCodeService(validateService)
    } else {
      new LiveSendCodeService(verificationCodeGenerator, auditService, verificationCodeService, validateService, userNotificationsConnector, metricsService)
    }
}
