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

package uk.gov.hmrc.cipphonenumberverification.repositories

import play.api.Logging
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.{Passcode, PhoneNumber}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationLong

class PasscodeCacheRepository @Inject()(mongoComponent: MongoComponent, config: AppConfig, timestampSupport: TimestampSupport)(implicit ec: ExecutionContext)
  extends MongoCacheRepository (
    mongoComponent = mongoComponent,
    collectionName = "cip-phone-number-verification",
    ttl = config.cacheExpiry.minutes,
    timestampSupport = timestampSupport,
    cacheIdType = CacheIdType.SimpleCacheId) with Logging {

    def persistPasscode(phoneNumber: PhoneNumber, passcode: Passcode)(implicit hc: HeaderCarrier): Future[Passcode] = {
        logger.debug(s"Saving passcode for phone number ${phoneNumber.phoneNumber} to the database")
        put(phoneNumber.phoneNumber)(DataKey("cip-phone-number-verification"), passcode).map(_ => passcode)
    }

}
