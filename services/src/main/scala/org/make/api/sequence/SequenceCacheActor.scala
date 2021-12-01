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

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import cats.data.{NonEmptyList => Nel}
import eu.timepit.refined.auto.autoUnwrap
import org.make.api.sequence.SequenceCacheActor._
import org.make.api.technical.ActorProtocol
import org.make.api.technical.sequence.SequenceCacheConfiguration
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.question.QuestionId

import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.concurrent.duration.Deadline

object SequenceCacheActor {

  def apply(
    questionId: QuestionId,
    reloadProposals: QuestionId => Future[Nel[IndexedProposal]],
    config: SequenceCacheConfiguration
  ): Behavior[Protocol] = {
    Behaviors.withStash(1000) { buffer =>
      Behaviors.setup { context =>
        fetchProposals(questionId, reloadProposals, context)()
        new SequenceCacheActor(context, buffer, config, fetchProposals(questionId, reloadProposals, context)).init()
      }
    }
  }

  def fetchProposals(
    questionId: QuestionId,
    reloadProposals: QuestionId => Future[Nel[IndexedProposal]],
    context: ActorContext[Protocol]
  ): () => Unit = () => {
    context.pipeToSelf(reloadProposals(questionId)) {
      case Success(proposals) => SetProposalsPoolSuccess(proposals)
      case Failure(e)         => SetProposalsPoolFailure(e)
    }
  }

  sealed trait Protocol extends ActorProtocol

  sealed trait Command extends Protocol
  sealed trait Response extends Protocol

  final case class GetProposal(replyTo: ActorRef[IndexedProposal]) extends Command
  final case class SetProposalsPoolSuccess(proposalsPool: Nel[IndexedProposal]) extends Command
  final case class SetProposalsPoolFailure(error: Throwable) extends Command
  case object Expire extends Command

  def name(questionId: QuestionId): String = questionId.value
}

class SequenceCacheActor private (
  context: ActorContext[Protocol],
  buffer: StashBuffer[Protocol],
  config: SequenceCacheConfiguration,
  fetchProposals: () => Unit
) {

  private val untilRefresh: Int = config.proposalsPoolSize * config.cacheRefreshCycles

  private def newDeadline: Deadline = Deadline.now + config.inactivityTimeout

  def init(): Behavior[Protocol] = {
    Behaviors.receiveMessage {
      case SetProposalsPoolSuccess(proposalsPool) =>
        buffer.unstashAll(cache(untilRefresh, proposalsPool, proposalsPool.iterator, newDeadline))
      case SetProposalsPoolFailure(e) =>
        context.log.error("Refreshing cache failed", e)
        Behaviors.stopped
      case cmd =>
        buffer.stash(cmd)
        Behaviors.same
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  def cache(
    counter: Int,
    proposalsPool: Nel[IndexedProposal],
    iterator: Iterator[IndexedProposal],
    deadline: Deadline
  ): Behavior[Protocol] = {
    Behaviors.receiveMessage {
      case GetProposal(replyTo) =>
        if (counter == 0) {
          fetchProposals()
        }
        if (iterator.hasNext) {
          replyTo ! iterator.next()
          cache(counter - 1, proposalsPool, iterator, newDeadline)
        } else {
          val it = proposalsPool.iterator
          replyTo ! it.next()
          cache(counter - 1, proposalsPool, it, newDeadline)
        }
      case Expire =>
        if (deadline.isOverdue()) {
          Behaviors.stopped
        } else {
          Behaviors.same
        }
      case SetProposalsPoolSuccess(proposalsPool) =>
        cache(untilRefresh, proposalsPool, proposalsPool.iterator, newDeadline)
      case SetProposalsPoolFailure(e) =>
        context.log.error("Refreshing cache failed", e)
        Behaviors.stopped
    }
  }
}
