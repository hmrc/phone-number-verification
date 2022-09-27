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

package uk.gov.hmrc.cipphonenumberverification.config

import play.api.{ConfigLoader, Configuration}

case class GovNotifyConfig(
                            host: String,
                            templateId: String,
                            govUkNotifyApiKeyIssUuid: String,
                            govUkNotifyApiKeySecretKeyUuid: String,
                            cbConfig: CircuitBreakerConfig
                          )

object GovNotifyConfig {
  implicit lazy val configLoader: ConfigLoader[GovNotifyConfig] =
    ConfigLoader {
      rootConfig => path =>
        val config = Configuration(rootConfig.getConfig(path))
        GovNotifyConfig(
          config.get[String]("host"),
          config.get[String]("template_id"),
          config.get[String]("api-key.iss-uuid"),
          config.get[String]("api-key.secret-key-uuid"),
          config.get[CircuitBreakerConfig]("circuit-breaker")
        )
    }
}

