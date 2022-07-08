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
import play.api.http.HttpEntity
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.i18n.{Langs, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.Results.{Accepted, InternalServerError, Ok}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.models.{ErrorResponse, Passcode, PhoneNumber, VerificationStatus}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx}
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class VerifyService @Inject()(passcodeCacheRepository: PasscodeCacheRepository,
                              validatorConnector: ValidateConnector,
                              govUkConnector: GovUkConnector,
                              messagesApi: MessagesApi, langs: Langs)
                             (implicit val executionContext: ExecutionContext) extends Logging {

  private[services] def passcodeGenerator = {
    val codeSize = 6
    Random.alphanumeric
      .filterNot(_.isDigit)
      .filterNot(_.isLower)
      .filterNot(Set('A', 'E', 'I', 'O', 'U'))
      .take(codeSize).mkString
  }

  private[services] def persistPasscode(phoneNumber: PhoneNumber): Future[Option[Passcode]] = {
    val passcode = passcodeGenerator

    passcodeCacheRepository.put(phoneNumber.phoneNumber)(DataKey("cip-phone-number-verification"),
      Passcode(phoneNumber.phoneNumber, passcode)) map { _ =>
        Option(Passcode(phoneNumber.phoneNumber, passcode))
    }
  }

  def verify(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier): Future[Result] = {
    validatorConnector.callService(phoneNumber) flatMap {
      case res if is2xx(res.header.status) =>
        persistPasscode(phoneNumber) flatMap {
          case Some(passcode) =>
            govUkConnector.sendPasscode(passcode) map {
              case Left(err) => ???
              case Right(response) if response.status == 201 => Accepted(Json.parse(s"""{"notification_id" : ${response.json("id")}}"""))
            }
          case None => Future(Result.apply(ResponseHeader(INTERNAL_SERVER_ERROR), HttpEntity.NoEntity))
        }
      case res if is4xx(res.header.status) => Future(res)
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
      case Some(_) => delete.map {
        _ => Ok(Json.toJson(VerificationStatus("Verified")))
      }
      case None => Future.successful(Ok(Json.toJson(VerificationStatus("Not verified"))))
    }).recover {
      case err =>
        logger.error(s"Database operation failed - ${err.getMessage}")
        InternalServerError(Json.toJson(ErrorResponse("DATABASE_OPERATION_FAIL", "Database operation failed")))
    }
  }
}
