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

package uk.gov.hmrc.cipphonenumberverification.controllers

import play.api.Logging
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.controllers.access.AccessChecker
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumberAndVerificationCode
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage
import uk.gov.hmrc.cipphonenumberverification.models.response.VerificationStatus
import uk.gov.hmrc.cipphonenumberverification.services.{LiveSendCodeService, SendCodeService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton()
class VerifyCodeController @Inject() (cc: ControllerComponents, service: SendCodeService, override val appConfig: AppConfig)
    extends BackendController(cc)
    with AccessChecker
    with Logging {

  import PhoneNumberAndVerificationCode.Implicits._

  def verifyCode: Action[JsValue] = accessCheckedAction(parse.json) {
    implicit request =>
      withJsonBody[PhoneNumberAndVerificationCode] {
        service.verifyVerificationCode
      }
  }

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(_) =>
        logger.warn(s"Failed to validate request")
        Future.successful(BadRequest(Json.toJson(VerificationStatus(StatusCode.VALIDATION_ERROR, StatusMessage.INVALID_TELEPHONE_NUMBER_OR_VERIFICATION_CODE))))
    }
}
