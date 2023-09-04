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
import uk.gov.hmrc.cipphonenumberverification.models
import uk.gov.hmrc.cipphonenumberverification.models.internal.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.request.PhoneNumber
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusCode.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.response.StatusMessage.INVALID_TELEPHONE_NUMBER
import uk.gov.hmrc.cipphonenumberverification.services.PasscodeService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class TestPasscodeController @Inject() (cc: ControllerComponents, service: PasscodeService, override val appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with AccessChecker
    with Logging {
  import PhoneNumber.Implicits._
  import PhoneNumberPasscodeData.Implicits._

  def retrievePasscode: Action[JsValue] = accessCheckedAction(parse.json) {
    implicit request =>
      withJsonBody[PhoneNumber] {
        phoneNumber =>
          service.retrievePasscode(phoneNumber.phoneNumber).map {
            case Some(phoneNumberPasscodeData) =>
              logger.info(s"PassCode found for phone number: ${phoneNumber.phoneNumber}")
              Ok(Json.toJson(phoneNumberPasscodeData))
            case None =>
              logger.info(s"No passcode found for phone number: ${phoneNumber.phoneNumber}")
              NoContent
          }
      }
  }

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(_) =>
        logger.warn(s"Failed to validate request")
        Future.successful(BadRequest(Json.toJson(models.response.VerificationStatus(VALIDATION_ERROR, INVALID_TELEPHONE_NUMBER))))
    }
}
