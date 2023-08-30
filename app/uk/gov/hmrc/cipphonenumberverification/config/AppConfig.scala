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

import play.api.{ConfigLoader, Configuration}

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject() (config: Configuration) {
  lazy val appName: String                              = config.get[String]("appName")
  lazy val cacheExpiry: Long                            = config.get[Long]("cache.expiry")
  lazy val phoneNotificationConfig: NotificationsConfig = config.get[NotificationsConfig]("microservice.services.user-notifications.phone")

  lazy val phoneNotificationAuthHeader = s"Basic $createAuth"

  private def createAuth = AppConfig.createAuth(appName, phoneNotificationConfig.authToken)

  def mustGetConfig[T: ConfigLoader](key: String): T =
    config.getOptional[T](key).getOrElse {
      throw new Exception("ERROR: Unable to find config item " + key)
    }

  def getConfig[T: ConfigLoader](key: String): Option[T] = config.getOptional[T](key)
}

object AppConfig {

  def createAuth(appName: String, authToken: String): String = Base64.getEncoder.encodeToString(
    s"$appName:$authToken".getBytes(StandardCharsets.UTF_8)
  )
}
