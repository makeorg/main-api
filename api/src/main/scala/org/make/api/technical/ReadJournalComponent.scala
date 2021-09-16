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

package org.make.api.technical

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, ReadJournal}
import org.make.api.ActorSystemComponent
import org.make.api.proposal.ProposalActor
import org.make.api.sessionhistory.ShardedSessionHistory
import org.make.api.technical.BaseReadJournalComponent.DefaultReadJournal
import org.make.api.userhistory.UserHistoryActor

trait BaseReadJournalComponent[+MakeReadJournal <: DefaultReadJournal] {
  def proposalJournal: MakeReadJournal
  def userJournal: MakeReadJournal
  def sessionJournal: MakeReadJournal
}

object BaseReadJournalComponent {
  type DefaultReadJournal = ReadJournal with CurrentPersistenceIdsQuery with CurrentEventsByPersistenceIdQuery
}

trait DefaultReadJournalComponent extends BaseReadJournalComponent[CassandraReadJournal] {
  self: ActorSystemComponent =>

  override def proposalJournal: CassandraReadJournal = ReadJournal.proposalJournal(actorSystem.toTyped)
  override def userJournal: CassandraReadJournal = ReadJournal.userJournal(actorSystem.toTyped)
  override def sessionJournal: CassandraReadJournal = ReadJournal.sessionJournal(actorSystem.toTyped)
}

object ReadJournal {
  def proposalJournal[T](system: ActorSystem[_]): T =
    PersistenceQuery(system = system).readJournalFor(ProposalActor.QueryJournalPluginId)
  def userJournal[T](system: ActorSystem[_]): T =
    PersistenceQuery(system = system).readJournalFor(UserHistoryActor.QueryJournalPluginId)
  def sessionJournal[T](system: ActorSystem[_]): T =
    PersistenceQuery(system = system).readJournalFor(ShardedSessionHistory.queryJournal)
}
