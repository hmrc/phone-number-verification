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

package uk.gov.hmrc.cipphonenumberverification.controllers

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cipphonenumberverification.controllers.actions.AllowListAction
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.services.JourneyService

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class JourneyController @Inject()(
                                   cc: ControllerComponents,
                                   allowListAction: AllowListAction,
                                   journeyService: JourneyService
                                 )(implicit ec: ExecutionContext)
  extends PhoneNumberVerificationBaseController(cc):

  private val authAction = allowListAction()

  def initJourney: Action[JourneyConfig] = authAction.async(parse.json[JourneyConfig]) { implicit request =>
    logger.info(s"Initializing journey with config: ${Json.toJson(request.body)}")

    journeyService.initJourney(request.body)
      .map { redirectUrl =>
        Created(Json.toJson(redirectUrl))
      }.recover {
        case ex: Exception =>
          logger.error("Failed to initialize journey", ex)
          InternalServerError
    }
  }

  def journeyDetails(journeyId: String): Action[AnyContent] = authAction.async { implicit request =>
      logger.info(s"Fetching journey details for journeyId: $journeyId")
  
      journeyService.getJourneyDetails(journeyId)
      .map {
          case Some(details) => Ok(Json.toJson(details))
          case None => NotFound
      }.recover {
          case ex: Exception =>
          logger.error(s"Failed to fetch journey details for journeyId: $journeyId", ex)
          InternalServerError
      }
  }