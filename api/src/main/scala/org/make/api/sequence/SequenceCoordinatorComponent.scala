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

package org.make.api.sequence

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.TimeSettings
import org.make.core.RequestContext
import org.make.core.sequence._

import scala.concurrent.Future

trait SequenceCoordinatorComponent {
  def sequenceCoordinator: ActorRef
}

trait SequenceCoordinatorService {

  /**
    * Retrieve a Sequence without logging it
    *
    * @param sequenceId the sequence to retrieve
    *
    * @return the given sequence if exists
    */
  def getSequence(sequenceId: SequenceId): Future[Option[Sequence]]

  /**
    * Retrieves a sequence by id and log the fact that it was seen
    *
    * @param sequenceId the sequence viewed by the user
    * @param requestContext the context of the request
    * @return the sequence as viewed by the user
    */
  def viewSequence(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]]
  def create(command: CreateSequenceCommand): Future[SequenceId]
  def update(command: UpdateSequenceCommand): Future[Option[Sequence]]
  def removeProposals(command: RemoveProposalsSequenceCommand): Future[Option[Sequence]]
  def addProposals(command: AddProposalsSequenceCommand): Future[Option[Sequence]]
  // toDo:
  // def publish(command: PublishSequenceCommand): Future[Option[Sequence]]
  // def unpublish(command: UnpublishSequenceCommand): Future[Option[Sequence]]
}

trait SequenceCoordinatorServiceComponent {
  def sequenceCoordinatorService: SequenceCoordinatorService
}

trait DefaultSequenceCoordinatorServiceComponent extends SequenceCoordinatorServiceComponent with StrictLogging {
  self: SequenceCoordinatorComponent =>

  override lazy val sequenceCoordinatorService: SequenceCoordinatorService = new SequenceCoordinatorService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def getSequence(sequenceId: SequenceId): Future[Option[Sequence]] = {
      (sequenceCoordinator ? GetSequence(sequenceId, RequestContext.empty)).mapTo[Option[Sequence]]
    }

    override def viewSequence(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]] = {
      (sequenceCoordinator ? ViewSequenceCommand(sequenceId, requestContext)).mapTo[Option[Sequence]]
    }

    override def create(command: CreateSequenceCommand): Future[SequenceId] = {
      (sequenceCoordinator ? command).mapTo[SequenceId]
    }

    override def update(command: UpdateSequenceCommand): Future[Option[Sequence]] = {
      (sequenceCoordinator ? command).mapTo[Option[Sequence]]
    }

    override def removeProposals(command: RemoveProposalsSequenceCommand): Future[Option[Sequence]] = {
      (sequenceCoordinator ? command).mapTo[Option[Sequence]]
    }

    override def addProposals(command: AddProposalsSequenceCommand): Future[Option[Sequence]] = {
      (sequenceCoordinator ? command).mapTo[Option[Sequence]]
    }
  }
}
