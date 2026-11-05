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
import uk.gov.hmrc.cipphonenumberverification.models.journey.JourneyConfig
import uk.gov.hmrc.cipphonenumberverification.repositories.models.JourneyConfigMongoDocument
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyMongoRepository])
trait JourneyRepository:
  def set(journeyId: String, journey: JourneyConfig): Future[Unit]

  def get(journeyId: String): Future[Option[JourneyConfig]]

@Singleton
class JourneyMongoRepository @Inject()(mongoComponent: MongoComponent, appConfig: AppConfig)
                                      (implicit ec: ExecutionContext)

  extends PlayMongoRepository[JourneyConfigMongoDocument](
    collectionName = "journey-config",
    mongoComponent = mongoComponent,
    domainFormat = JourneyConfigMongoDocument.formats,
    indexes = Seq(
      IndexModel(Indexes.ascending("journeyCreatedAt"), IndexOptions().name("ttl").expireAfter(appConfig.journeyCacheExpiry, TimeUnit.MINUTES)),
      IndexModel(Indexes.ascending("journeyId"), IndexOptions().name("journeyId").unique(true)),
      IndexModel(Indexes.ascending("journeyConfig.credentialId"), IndexOptions().name("credentialId").unique(false))
    )
  ) with JourneyRepository:

  private def byJourneyId(journeyId: String): Bson =
    Filters.equal("journeyId", journeyId)

  private def keepAlive(journeyId: String): Future[Unit] =
    collection.updateOne(
      filter = byJourneyId(journeyId),
      update = Updates.set("journeyCreatedAt", Instant.now())
    ).toFuture().map(_ => ())

  def set(journeyId: String, journey: JourneyConfig): Future[Unit] =
    collection.insertOne(JourneyConfigMongoDocument(journeyId, Instant.now(), journey)).toFuture().map(_ => ())

  def get(journeyId: String): Future[Option[JourneyConfig]] = {
    keepAlive(journeyId).flatMap { _ =>
      collection.find(byJourneyId(journeyId)).headOption().map(_.map(_.journeyConfig))
    }
  }
