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

package uk.gov.hmrc.cipphonenumberverification.repositories

import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.models.PhoneNumberPasscodeData
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndPasscode
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationLong

class PasscodeCacheRepository @Inject() (mongoComponent: MongoComponent, config: AppConfig, timestampSupport: TimestampSupport)(implicit ec: ExecutionContext)
    extends MongoCacheRepository(mongoComponent = mongoComponent,
                                 collectionName = "phone-number-verification",
                                 ttl = config.cacheExpiry.minutes,
                                 timestampSupport = timestampSupport,
                                 cacheIdType = CacheIdType.SimpleCacheId
    )

object PasscodeCacheRepository {
  val phoneNumberPasscodeDataDataKey: DataKey[PhoneNumberPasscodeData] = DataKey("phone-number-verification")
  val phoneNumberPasscodeDataKey: DataKey[PhoneNumberAndPasscode]      = DataKey("phone-number-verification")
}
