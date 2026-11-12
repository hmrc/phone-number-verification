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
import play.api.Logging
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.services.JourneyService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class JourneyRequest[A](
                         val journeyId: String,
                         val journeyConfig: JourneyConfig,
                         request: Request[A]
                       ) extends WrappedRequest[A](request)

@ImplementedBy(classOf[JourneyActionImpl])
trait JourneyAction {
  def apply(journeyId: String): ActionBuilder[JourneyRequest, AnyContent]
}

@Singleton
class JourneyActionImpl @Inject()(
                                  journeyService: JourneyService,
                                  parsers: PlayBodyParsers
                                )(implicit ec: ExecutionContext)
  extends JourneyAction with Logging:

  override def apply(journeyId: String): ActionBuilder[JourneyRequest, AnyContent] =
    new ActionBuilder[JourneyRequest, AnyContent]:

      override def parser: BodyParser[AnyContent] = parsers.anyContent

      override def executionContext: ExecutionContext = ec

      override def invokeBlock[A](request: Request[A], block: JourneyRequest[A] => Future[Result]): Future[Result] =
        journeyService.getJourneyDetails(journeyId).transformWith {
          case Success(Some(journey)) => block(new JourneyRequest(journeyId, journey, request))
          case Success(None) => Future.successful(NotFound)
          case Failure(ex) =>
            logger.error(s"Failed to fetch journey details for journeyId: $journeyId", ex)
            Future.successful(InternalServerError)
        }
