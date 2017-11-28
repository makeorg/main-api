package org.make.api.technical

import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.{CurrentPersistenceIdsQuery, ReadJournal}
import org.make.api.ActorSystemComponent

trait ReadJournalComponent {
  def readJournal: ReadJournal with CurrentPersistenceIdsQuery
}

trait DefaultReadJournalComponent extends ReadJournalComponent {
  self: ActorSystemComponent =>

  override def readJournal: ReadJournal with CurrentPersistenceIdsQuery =
    PersistenceQuery(system = actorSystem)
      .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
}
