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

import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VerifyPasscodeService @Inject()(passcodeCacheRepository: PasscodeCacheRepository)
                                     (implicit ec: ExecutionContext) {
  def persistPasscode(phoneNumberPasscodeData: PhoneNumberPasscodeData): Future[PhoneNumberPasscodeData] = {
    passcodeCacheRepository.put(phoneNumberPasscodeData.phoneNumber)(DataKey("cip-phone-number-verification"), phoneNumberPasscodeData)
      .map(_ => phoneNumberPasscodeData)
  }

  def retrievePasscode(phoneNumber: String): Future[Option[PhoneNumberPasscodeData]] = {
    passcodeCacheRepository.get[PhoneNumberPasscodeData](phoneNumber)(DataKey("cip-phone-number-verification"))
  }
}
