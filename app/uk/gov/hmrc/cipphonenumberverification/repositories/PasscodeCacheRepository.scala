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

import play.api.libs.json.{Reads, Writes}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.cipphonenumberverification.models.Passcode
import uk.gov.hmrc.mongo.cache.{CacheIdType, CacheItem, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class PasscodeCacheRepository @Inject()(
                                   mongoComponent  : MongoComponent,
                                   configuration   : Configuration,
                                   timestampSupport: TimestampSupport
                                 )(implicit ec: ExecutionContext)
                             extends MongoCacheRepository (
                                        mongoComponent   = mongoComponent,
                                        collectionName   = "cip-phone-number-verification",
                                        ttl              = configuration.get[FiniteDuration]("cache.expiry"),
                                        timestampSupport = timestampSupport,
                                        cacheIdType      = CacheIdType.SimpleCacheId) with Logging {

  def persist[T](id: String, key: String, data: T)(implicit writes: Writes[T], reads: Reads[T]): Future[Option[T]] =
    put(id)(DataKey(key), data).map(_.data.asOpt[T]).recover {
        case e =>
          logger.error(e.formatted("failed to persist passcode"))
          None
      }
}
