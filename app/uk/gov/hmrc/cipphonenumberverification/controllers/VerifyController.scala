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
import uk.gov.hmrc.cipphonenumberverification.access.AccessChecker
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes.VALIDATION_ERROR
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Message.INVALID_TELEPHONE_NUMBER
import uk.gov.hmrc.cipphonenumberverification.models.api.{ErrorResponse, PhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.api.PhoneNumber.verification._
import uk.gov.hmrc.cipphonenumberverification.services.VerifyService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class VerifyController @Inject() (cc: ControllerComponents,
                                  service: VerifyService,
                                  metricsService: MetricsService,
                                  override val appConfig: AppConfig,
                                  val ec: ExecutionContext
) extends BackendController(cc)
    with AccessChecker
    with Logging {
  implicit val ecc: ExecutionContext = ec

  def verify: Action[JsValue] = accessCheckedAction(parse.json) {
    implicit request =>
      // TODO create some form of response builder
      withJsonBody[PhoneNumber] {
        service.verifyPhoneNumber
      }.map {
        case (r: Result) if r.header.status == OK => NoContent
        case r: Result                            => BadRequest
      }
  }

  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    request.body.validate[T] match {
      case JsSuccess(payload, _) => f(payload)
      case JsError(_) =>
        metricsService.recordMetric("telephone_number_validation_failure")
        logger.warn("Failed to validate request")
        Future.successful(BadRequest(Json.toJson(ErrorResponse(VALIDATION_ERROR.id, INVALID_TELEPHONE_NUMBER))))
    }
}
