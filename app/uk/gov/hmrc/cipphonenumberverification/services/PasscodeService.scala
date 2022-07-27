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
import uk.gov.hmrc.cipphonenumberverification.models.{PhoneNumberAndOtp}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PasscodeService @Inject()(passcodeCacheRepository: PasscodeCacheRepository)
                               (implicit ec: ExecutionContext) extends Logging {
  def persistPasscode(phoneNumberAndOtp: PhoneNumberAndOtp): Future[PhoneNumberAndOtp] = {
    logger.debug(s"Storing phoneNumberAndOtp in database for ${phoneNumberAndOtp.phoneNumber}")
    passcodeCacheRepository.put(phoneNumberAndOtp.phoneNumber)(DataKey("cip-phone-number-verification"), phoneNumberAndOtp)
      .map(_ => phoneNumberAndOtp)
  }

  def retrievePasscode(phoneNumber: String): Future[Option[PhoneNumberAndOtp]] = {
    logger.debug(s"Retrieving phoneNumberAndOtp from database for $phoneNumber")
    passcodeCacheRepository.get[PhoneNumberAndOtp](phoneNumber)(DataKey("cip-phone-number-verification"))
  }

  def deletePasscode(phoneNumberAndOtp: PhoneNumberAndOtp): Future[Unit] = {
    logger.debug(s"Deleting phoneNumberAndOtp from database for ${phoneNumberAndOtp.phoneNumber}")
    passcodeCacheRepository.delete(phoneNumberAndOtp.phoneNumber)(DataKey("cip-phone-number-verification"))
  }
}
