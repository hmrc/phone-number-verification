/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalamock.handlers.CallHandler0
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite

trait MockAppConfig extends MockFactory:
  this: TestSuite =>

  lazy val mockAppConfig: AppConfig = mock[AppConfig]

  object MockAppConfig:

    def allowListEnabled(isEnabled: Boolean): CallHandler0[Boolean] =
      (() => mockAppConfig.allowListEnabled).expects().returning(isEnabled).anyNumberOfTimes()
      
    def allowedClients(clients: Set[String]): CallHandler0[Set[String]] =
      (() => mockAppConfig.allowedClients).expects().returning(clients).anyNumberOfTimes()

    def accessRequestFormUrl(url: String): CallHandler0[String] =
      (() => mockAppConfig.accessRequestFormUrl).expects().returning(url).anyNumberOfTimes()
      
    def journeyCacheExpiry(minutes: Long): CallHandler0[Long] =
      (() => mockAppConfig.journeyCacheExpiry).expects().returning(minutes).anyNumberOfTimes()