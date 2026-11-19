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

import com.google.inject.ImplementedBy
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.{AccessForbidden, Error}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class AllowListRequest[A](val userAgent: String, request: Request[A]) extends WrappedRequest[A](request)

@ImplementedBy(classOf[AllowListActionImpl])
trait AllowListAction {
  def apply(): ActionBuilder[AllowListRequest, AnyContent]
}

@Singleton
class AllowListActionImpl @Inject()(
                                     config: AppConfig,
                                     parsers: PlayBodyParsers
                                   )
                                   (implicit ec: ExecutionContext)
  extends AllowListAction:

  override def apply(): ActionBuilder[AllowListRequest, AnyContent] =
    new ActionBuilder[AllowListRequest, AnyContent]:

      override def parser: BodyParser[AnyContent] = parsers.anyContent

      override def executionContext: ExecutionContext = ec

      override def invokeBlock[A](request: Request[A], block: AllowListRequest[A] => Future[Result]): Future[Result] =
        
        val userAgent = request.headers.get(HeaderNames.USER_AGENT)
          
        if (!config.allowListEnabled || userAgent.forall(config.allowedClients.contains)) {
          assert(userAgent.isDefined, "User-Agent header is missing in the request")
          block(new AllowListRequest(userAgent.get, request))
        } else
          Future.successful(
            Forbidden(Json.toJson[Error](AccessForbidden(userAgent.getOrElse("unknown"), config.accessRequestFormUrl)))
          )