package org.make.api.technical

import akka.actor.Actor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, ReadJournal}
import org.make.api.ActorSystemComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal

trait ReadJournalComponent {
  def readJournal: MakeReadJournal
}

object ReadJournalComponent {
  type MakeReadJournal = ReadJournal with CurrentPersistenceIdsQuery with CurrentEventsByPersistenceIdQuery
}

trait DefaultReadJournalComponent extends ReadJournalComponent {
  self: ActorSystemComponent =>

  override def readJournal: MakeReadJournal =
    PersistenceQuery(system = actorSystem)
      .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
}

trait ActorReadJournalComponent extends ReadJournalComponent {
  self: Actor =>

  override def readJournal: MakeReadJournal =
    PersistenceQuery(system = context.system)
      .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
}
