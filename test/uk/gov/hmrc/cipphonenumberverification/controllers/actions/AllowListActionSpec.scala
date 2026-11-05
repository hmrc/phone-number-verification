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

package uk.gov.hmrc.cipphonenumberverification.controllers.actions


import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.{HeaderNames, MimeTypes, Status}
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cipphonenumberverification.BaseSpec
import uk.gov.hmrc.cipphonenumberverification.config.MockAppConfig
import uk.gov.hmrc.cipphonenumberverification.models.{AccessForbidden, Error}

class AllowListActionSpec extends BaseSpec with MockAppConfig:

  val action = new AllowListActionImpl(mockAppConfig, Helpers.stubPlayBodyParsers())

  ".apply()" when:

    val baseRequest = FakeRequest("POST", "/journey/init")

    "the allow list is enabled" when:

      "the User-Agent is on the allow list" should:

        "return a OK (200)" in:

          MockAppConfig.allowListEnabled(true)
          MockAppConfig.allowedClients(Set("foo-agent", "bar-agent"))

          val fakeRequest = baseRequest
            .withHeaders(
              HeaderNames.USER_AGENT -> "foo-agent",
              HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
            )

          val result = action()(Results.Ok("allowed"))(fakeRequest)

          status(result) shouldBe Status.OK
          contentAsString(result) shouldBe "allowed"

      "the User-Agent is NOT on the allow list" should :

        "return a Forbidden (403)" in :

          MockAppConfig.allowListEnabled(true)
          MockAppConfig.allowedClients(Set("foo-agent", "bar-agent"))
          MockAppConfig.accessRequestFormUrl("/request-access")

          val fakeRequest = baseRequest
            .withHeaders(
              HeaderNames.USER_AGENT -> "other-agent",
              HeaderNames.CONTENT_TYPE -> MimeTypes.JSON
            )

          val result = action()(Results.Ok("allowed"))(fakeRequest)

          status(result) shouldBe Status.FORBIDDEN

          contentAsJson(result) shouldBe Json.toJson[Error](
            AccessForbidden(
              userAgent = "other-agent",
              formUrl = "/request-access"
            )
          )
