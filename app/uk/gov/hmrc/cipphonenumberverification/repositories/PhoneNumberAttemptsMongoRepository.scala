/*
 * Copyright 2026 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.models.PhoneNumberAttemptsMongoDocument
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PhoneNumberAttemptsMongoRepository])
trait PhoneNumberAttemptsRepository:
  def recordFailedAttempt(credentialId: String, phoneNumber: String): Future[PhoneNumberAttemptsMongoDocument]

  def getAttempts(credentialId: String): Future[Seq[PhoneNumberAttemptsMongoDocument]]

/** Tracks failed verification attempts per credentialId and phone number. Unlike the journey collection (15 minute TTL),
  * documents live for 24 hours from the first failed attempt so a user can be locked out across multiple journeys.
  */
@Singleton
class PhoneNumberAttemptsMongoRepository @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[PhoneNumberAttemptsMongoDocument](
      collectionName = "phone-number-attempts",
      mongoComponent = mongoComponent,
      domainFormat = PhoneNumberAttemptsMongoDocument.formats,
      indexes = Seq(
        IndexModel(Indexes.ascending("createdAt"), IndexOptions().name("ttl").expireAfter(appConfig.phoneNumberAttemptsExpiry, TimeUnit.HOURS)),
        IndexModel(Indexes.ascending("credentialId", "phoneNumber"), IndexOptions().name("credentialIdPhoneNumber").unique(true))
      )
    )
    with PhoneNumberAttemptsRepository:

  private def byCredentialId(credentialId: String): Bson =
    Filters.equal("credentialId", credentialId)

  def recordFailedAttempt(credentialId: String, phoneNumber: String): Future[PhoneNumberAttemptsMongoDocument] = {
    val now = Instant.now()
    collection
      .findOneAndUpdate(
        filter = Filters.and(byCredentialId(credentialId), Filters.equal("phoneNumber", phoneNumber)),
        update = Updates.combine(
          Updates.inc("failedAttempts", 1),
          Updates.set("lastFailedAttemptAt", now),
          Updates.setOnInsert("createdAt", now)
        ),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      )
      .toFuture()
  }

  def getAttempts(credentialId: String): Future[Seq[PhoneNumberAttemptsMongoDocument]] =
    collection.find(byCredentialId(credentialId)).toFuture()
