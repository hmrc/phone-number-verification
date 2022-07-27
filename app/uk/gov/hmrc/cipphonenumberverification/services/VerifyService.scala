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
import play.api.mvc.Result
import play.api.mvc.Results.{Accepted, BadRequest, InternalServerError, Ok}
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.models.{ErrorResponse, IndeterminateResponse, Passcode, PhoneNumber, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx}
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import java.security.SecureRandom

class VerifyService @Inject()(passcodeCacheRepository: PasscodeCacheRepository,
                              validatorConnector: ValidateConnector,
                              govUkConnector: GovUkConnector)
                             (implicit val executionContext: ExecutionContext) extends Logging {

  private[services] def otpGenerator = {
    val sb = new StringBuilder()
    val passcodeSize = 6
    val chrsToChooseFrom = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val secureRandom = SecureRandom.getInstanceStrong
    secureRandom.ints(passcodeSize, 0, chrsToChooseFrom.length)
      .mapToObj((i: Int) => chrsToChooseFrom.charAt(i))
      .forEach(x => sb.append(x))
    sb.mkString
  }

  def verify(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier): Future[Result] = {

    val otp = otpGenerator
    val passcode = Passcode(phoneNumber.phoneNumber, otp)

    passcodeCacheRepository.put(phoneNumber.phoneNumber)(DataKey("cip-phone-number-verification"), passcode).map(_ => passcode)

    validatorConnector.callService(phoneNumber) flatMap {
      case res if is2xx(res.status) =>
        (res.json \ "phoneNumberType").as[String] match {
          case "Mobile" =>
            (persistPasscode(phoneNumber) flatMap { passcode =>
              govUkConnector.sendPasscode(passcode) map {
                case Left(err) => ??? //TODO: CAV-163
                case Right(response) if response.status == 201 => Accepted(Json.parse(s"""{"notificationId" : ${response.json("id")}}"""))
              }
            }).recover {
              case err =>
                logger.error(s"Database operation failed - ${err.getMessage}")
                InternalServerError(Json.toJson(ErrorResponse("DATABASE_OPERATION_FAIL", "Database operation failed")))
            }
          case _ => Future(Ok(Json.toJson(IndeterminateResponse("Indeterminate", "Only mobile numbers can be verified"))))
        }
      case res if is4xx(res.status) => Future(BadRequest(res.json))
    }
  }

  def verifyOtp(passcode: Passcode): Future[Result] = {
    def get: Future[Option[Passcode]] = {
      logger.debug(s"Retrieving passcode from database for ${passcode.phoneNumber}")
      passcodeCacheRepository.get[Passcode](passcode.phoneNumber)(DataKey("cip-phone-number-verification"))
    }

    def delete: Future[Unit] = {
      logger.debug(s"Deleting passcode from database for ${passcode.phoneNumber}")
      passcodeCacheRepository.delete(passcode.phoneNumber)(DataKey("cip-phone-number-verification"))
    }

    (get flatMap {
      case Some(actualPasscode) => {
        if (passcode.otp.equals(actualPasscode.otp)) {
          delete.map {
            _ => Ok(Json.toJson(VerificationStatus("Verified")))
          }
        } else {
          Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
        }
      }
      case None => Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
    }).recover {
      case err =>
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(ErrorResponse("DATABASE_OPERATION_FAIL", "Database operation failed")))
    }
  }

  private[services] def persistPasscode(phoneNumber: PhoneNumber) = {
    logger.debug(s"Storing passcode in database for ${phoneNumber.phoneNumber}")
    val otp = otpGenerator
    val passcode = Passcode(phoneNumber.phoneNumber, otp)
    passcodeCacheRepository.put(phoneNumber.phoneNumber)(DataKey("cip-phone-number-verification"), passcode).map(_ => passcode)
  }
}

//{
//          (persistPasscode(phoneNumber) flatMap { passcode =>
//            govUkConnector.sendPasscode(passcode) map {
//              case Left(err) => ??? //TODO: CAV-163
//              case Right(response) if response.status == 201 => Accepted(Json.parse(s"""{"notificationId" : ${response.json("id")}}"""))
//            }
//          }).recover {
//            case err =>
//              logger.error(s"Database operation failed - ${err.getMessage}")
//              InternalServerError(Json.toJson(ErrorResponse("DATABASE_OPERATION_FAIL", "Database operation failed")))
//          }

