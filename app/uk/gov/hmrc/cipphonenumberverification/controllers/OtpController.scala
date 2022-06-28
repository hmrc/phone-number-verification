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

package uk.gov.hmrc.cipphonenumberverification.controllers

import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.cipphonenumberverification.models.{ErrorResponse, Passcode}
import uk.gov.hmrc.cipphonenumberverification.services.VerifyService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton()
class OtpController @Inject()(cc: ControllerComponents, service: VerifyService)
  extends BackendController(cc)
    with Logging {

  def verifyOtp: Action[JsValue] = Action(parse.json).async { implicit request =>
    withJsonBody[Passcode] {
      service.verifyOtp
    }
  }

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(_)) =>
        logger.warn(s"Failed to validate request")
        Future.successful(BadRequest(Json.toJson(ErrorResponse("VALIDATION_ERROR", cc.messagesApi("error.validation")(cc.langs.availables.head)))))
      case Failure(e) =>
        logger.warn(s"Failed to validate request")
        Future.successful(BadRequest(Json.toJson(ErrorResponse("VALIDATION_ERROR", e.getMessage))))
    }
}
