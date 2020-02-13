/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.organisation

import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor}
import org.make.api.user.UserProducerActor
import org.make.api.userhistory.UserEvent
import org.make.api.userhistory.UserEventWrapper
import org.make.api.userhistory.{OrganisationRegisteredEvent, OrganisationUpdatedEvent}
import org.make.core.AvroSerializers
import org.make.core.user.UserId
import org.make.core.user.indexed.IndexedOrganisation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OrganisationConsumerActor(organisationService: OrganisationService,
                                override val elasticsearchOrganisationAPI: OrganisationSearchEngine,
                                override val elasticsearchConfiguration: ElasticsearchConfiguration)
    extends KafkaConsumerActor[UserEventWrapper]
    with ActorEventBusServiceComponent
    with AvroSerializers
    with ElasticsearchConfigurationComponent
    with OrganisationSearchEngineComponent
    with ActorLogging {

  override protected lazy val kafkaTopic: String = UserProducerActor.topicKey
  override protected val format: RecordFormat[UserEventWrapper] = UserEventWrapper.recordFormat

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: UserEventWrapper): Future[_] = {
    message.event match {
      case event: OrganisationRegisteredEvent => onCreateOrUpdate(event)
      case event: OrganisationUpdatedEvent    => onCreateOrUpdate(event)
      case event                              => doNothing(event)
    }
  }

  def onCreateOrUpdate(event: UserEvent): Future[Done] = {
    retrieveAndShapeOrganisation(event.userId).flatMap(indexOrUpdate)
  }

  def indexOrUpdate(organisation: IndexedOrganisation): Future[Done] = {
    log.debug(s"Indexing $organisation")
    elasticsearchOrganisationAPI
      .findOrganisationById(organisation.organisationId)
      .flatMap {
        case None => elasticsearchOrganisationAPI.indexOrganisation(organisation)
        case Some(found) =>
          elasticsearchOrganisationAPI.updateOrganisation(organisation.copy(countsByQuestion = found.countsByQuestion))
      }
  }

  private def retrieveAndShapeOrganisation(id: UserId): Future[IndexedOrganisation] = {
    organisationService.getOrganisation(id).flatMap {
      case None               => Future.failed(new IllegalArgumentException(s"Organisation ${id.value} doesn't exist"))
      case Some(organisation) => Future.successful(IndexedOrganisation.createFromOrganisation(organisation))
    }
  }

  override val groupId = "organisation-consumer"
}

object OrganisationConsumerActor {
  def props(organisationService: OrganisationService,
            elasticsearchOrganisationAPI: OrganisationSearchEngine,
            elasticsearchConfiguration: ElasticsearchConfiguration): Props =
    Props(new OrganisationConsumerActor(organisationService, elasticsearchOrganisationAPI, elasticsearchConfiguration))
  val name: String = "organisation-consumer"
}
