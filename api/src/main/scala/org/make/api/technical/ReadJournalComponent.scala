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

import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, ReadJournal}
import org.make.api.ActorSystemComponent
import org.make.api.proposal.ShardedProposal
import org.make.api.sequence.ShardedSequence
import org.make.api.sessionhistory.ShardedSessionHistory
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.userhistory.ShardedUserHistory

trait ReadJournalComponent {
  def proposalJournal: MakeReadJournal
  def sequenceJournal: MakeReadJournal
  def userJournal: MakeReadJournal
  def sessionJournal: MakeReadJournal
}

object ReadJournalComponent {
  type MakeReadJournal = ReadJournal with CurrentPersistenceIdsQuery with CurrentEventsByPersistenceIdQuery
}

trait DefaultReadJournalComponent extends ReadJournalComponent {
  self: ActorSystemComponent =>

  override def proposalJournal: MakeReadJournal =
    PersistenceQuery(system = actorSystem)
      .readJournalFor[CassandraReadJournal](ShardedProposal.queryJournal)
  override def sequenceJournal: MakeReadJournal =
    PersistenceQuery(system = actorSystem)
      .readJournalFor[CassandraReadJournal](ShardedSequence.queryJournal)
  override def userJournal: MakeReadJournal =
    PersistenceQuery(system = actorSystem)
      .readJournalFor[CassandraReadJournal](ShardedUserHistory.queryJournal)
  override def sessionJournal: MakeReadJournal =
    PersistenceQuery(system = actorSystem)
      .readJournalFor[CassandraReadJournal](ShardedSessionHistory.queryJournal)
}

trait ActorReadJournalComponent extends ReadJournalComponent {
  self: Actor =>

  override def proposalJournal: MakeReadJournal =
    PersistenceQuery(system = context.system)
      .readJournalFor[CassandraReadJournal](ShardedProposal.queryJournal)
  override def sequenceJournal: MakeReadJournal =
    PersistenceQuery(system = context.system)
      .readJournalFor[CassandraReadJournal](ShardedSequence.queryJournal)
  override def userJournal: MakeReadJournal =
    PersistenceQuery(system = context.system)
      .readJournalFor[CassandraReadJournal](ShardedUserHistory.queryJournal)
  override def sessionJournal: MakeReadJournal =
    PersistenceQuery(system = context.system)
      .readJournalFor[CassandraReadJournal](ShardedSessionHistory.queryJournal)
}
