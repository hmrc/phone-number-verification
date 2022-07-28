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

package uk.gov.hmrc.cipphonenumberverification.services

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Results.{Accepted, BadRequest, InternalServerError, Ok}
import uk.gov.hmrc.cipphonenumberverification.connectors.GovUkConnector
import uk.gov.hmrc.cipphonenumberverification.models._
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class VerifyHelper @Inject()(passcodeService: PasscodeService, govUkConnector: GovUkConnector) extends Logging {

  protected def processResponse(res: HttpResponse,  phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier) = res match {
    case _ if is2xx(res.status) => processValidPhoneNumber(res, phoneNumber)
    case _ if is4xx(res.status) => Future(BadRequest(res.json))
  }

  private def processValidPhoneNumber(res: HttpResponse, phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier) = (res.json \ "phoneNumberType").as[String] match {
    case "Mobile" =>
      passcodeService.persistPasscode(phoneNumber) flatMap sendPasscode recover {
        case err =>
          logger.error(s"Database operation failed - ${err.getMessage}")
          InternalServerError(Json.toJson(ErrorResponse("DATABASE_OPERATION_FAIL", "Database operation failed")))
      }
    case _ => Future(Ok(Json.toJson(Indeterminate("Indeterminate", "Only mobile numbers can be verified"))))
  }

  private def sendPasscode(passcode: Passcode)(implicit hc: HeaderCarrier) = (govUkConnector.sendPasscode(passcode) map {
    case Left(err) => ??? //TODO: CAV-163
      logger.error(s"Gov Notify failure - to be covered by CAV-163")
      InternalServerError(Json.toJson(ErrorResponse("EXTERNAL_SYSTEM_FAIL", "sending to Gov Notify failed")))
    case Right(response) if response.status == 201 => Accepted(Json.toJson(NotificationId(response.json("id").as[String])))
  }) recover {
    case err =>
      logger.error(s"GOVNOTIFY operation failed - ${err.getMessage}")
      InternalServerError(Json.toJson(ErrorResponse("GOVNOTIFY_OPERATION_FAIL", "Gov Notify operation failed")))
  }

  protected def processPasscode(enteredPasscode: Passcode, storedPasscodeOpt: Option[Passcode]) = storedPasscodeOpt match {
    case Some(storedPasscode) if enteredPasscode.otp.equals(storedPasscode.otp) => passcodeService.delete(enteredPasscode) map {
      _ => Ok(Json.toJson(VerificationStatus("Verified")))}
    case _ => Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
  }
}
