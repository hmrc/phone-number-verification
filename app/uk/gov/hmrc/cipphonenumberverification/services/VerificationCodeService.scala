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

package uk.gov.hmrc.cipphonenumberverification.services

import play.api.Logging
import uk.gov.hmrc.cipphonenumberverification.models.internal.PhoneNumberVerificationCodeData
import uk.gov.hmrc.cipphonenumberverification.repositories.VerificationCodeCacheRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VerificationCodeService @Inject() (verificationCodeCacheRepository: VerificationCodeCacheRepository)(implicit ec: ExecutionContext) extends Logging {
  import PhoneNumberVerificationCodeData.Implicits._

  def persistVerificationCode(phoneNumberAndVerificationCode: PhoneNumberVerificationCodeData): Future[PhoneNumberVerificationCodeData] = {
    logger.debug(s"Storing phoneNumberAndVerificationCode in database")
    verificationCodeCacheRepository
      .put(phoneNumberAndVerificationCode.phoneNumber)(VerificationCodeCacheRepository.phoneNumberVerificationCodeDataDataKey, phoneNumberAndVerificationCode)
      .map(
        _ => phoneNumberAndVerificationCode
      )
  }

  def retrieveVerificationCode(phoneNumber: String): Future[Option[PhoneNumberVerificationCodeData]] = {
    logger.debug(s"Retrieving phoneNumberAndverificationCode from database")
    verificationCodeCacheRepository.get[PhoneNumberVerificationCodeData](phoneNumber)(VerificationCodeCacheRepository.phoneNumberVerificationCodeDataDataKey)
  }

}
