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

import play.api.i18n.{Langs, MessagesApi}
import play.api.libs.json.{Json, Reads}
import play.api.mvc.Results.BadRequest
import uk.gov.hmrc.cipphonenumberverification.connectors.ValidateConnector
import uk.gov.hmrc.cipphonenumberverification.models.{ErrorResponse, Passcode, PhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.{is2xx, is4xx}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Success, Try}

class VerifyService @Inject()(passcodeCacheRepository: PasscodeCacheRepository,
                              validatorConnector: ValidateConnector,
                              messagesApi: MessagesApi, langs: Langs) {

  private[services] def passcode = {
    val codeSize = 6
    Random.alphanumeric
      .filterNot(_.isDigit)
      .filterNot(_.isLower)
      .filterNot(Set('A', 'E', 'I', 'O', 'U'))
      .take(codeSize).mkString
  }

  private[services] def persistPasscode(phoneNumber: PhoneNumber)(implicit passcodeReads: Reads[Passcode]) = {
    Try(passcodeCacheRepository.persist(phoneNumber.phoneNumber,"cip-phone-number-verification", Passcode(phoneNumber.phoneNumber, passcode)))
  }

  def verifyDetails(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier) =
    validatorConnector.callService(phoneNumber) map {
      case res if is2xx(res.header.status) =>
        persistPasscode(phoneNumber) match {
          case Success(passcodeValue) => res //TODO send passcode to gov-notify - "passcodeValue" CAV-110
          case Failure(e) => BadRequest(Json.toJson(ErrorResponse("VERIFICATION_ERROR", messagesApi("error.failure")(langs.availables.head))))
        }
      case res if is4xx(res.header.status) => res
    }
}
