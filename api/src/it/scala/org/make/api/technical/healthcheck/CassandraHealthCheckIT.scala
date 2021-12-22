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

package org.make.api.technical.healthcheck

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.util
import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.make.api.docker.DockerCassandraService
import org.make.api.proposal.ProposalActorResponse.Envelope
import org.make.api.proposal.{ProposalActorProtocol, ProposalCommand, ProposalCoordinator, ProposeCommand}
import org.make.api.sessionhistory.SessionHistoryCoordinatorService
import org.make.api.technical.healthcheck.HealthCheck.Status
import org.make.api.technical.{DefaultIdGeneratorComponent, ReadJournal, TimeSettings}
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class CassandraHealthCheckIT
    extends ScalaTestWithActorTestKit(CassandraHealthCheckIT.actorSystem)
    with DefaultIdGeneratorComponent
    with MakeUnitTest
    with DockerCassandraService {

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  override val cassandraExposedPort: Int = CassandraHealthCheckIT.cassandraExposedPort
  implicit override val timeout: util.Timeout = TimeSettings.defaultTimeout
  implicit val ctx: ExecutionContext = global

  val LOCK_DURATION_MILLISECONDS: FiniteDuration = 42.milliseconds

  val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService = mock[SessionHistoryCoordinatorService]

  val coordinator: ActorRef[ProposalCommand] =
    ProposalCoordinator(system, sessionHistoryCoordinatorService, LOCK_DURATION_MILLISECONDS, idGenerator)

  Feature("Check Cassandra status") {
    Scenario("query proposal journal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()
      val proposalId = ProposalId("fake-proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = TestUtils.user(id = UserId("fake-user"), email = "fake@user.com", firstName = None, lastName = None),
        createdAt = DateHelper.now(),
        content = "This is a proposal",
        question = Question(
          questionId = QuestionId("fake-question"),
          slug = "fake-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "fake question",
          shortTitle = None,
          operationId = None
        ),
        initialProposal = false,
        probe.ref
      )

      probe.expectMessage(10.seconds, Envelope(proposalId))

      val proposalJournal: CassandraReadJournal = ReadJournal.proposalJournal(system)
      val hc = new CassandraHealthCheck(proposalJournal, system.settings.config)

      whenReady(hc.healthCheck(), Timeout(30.seconds)) { res =>
        res should be(Status.OK)
      }
    }
  }

}

object CassandraHealthCheckIT {
  val cassandraExposedPort = 15000
  // This configuration cannot be dynamic, port values _must_ match reality
  val port = 15100
  val configuration: String =
    s"""
       |akka {
       |
       |  actor {
       |
       |    provider = "akka.cluster.ClusterActorRefProvider"
       |
       |    serializers {
       |      make-serializer = "org.make.api.technical.MakeEventSerializer"
       |    }
       |    serialization-bindings {
       |      "org.make.core.MakeSerializable" = make-serializer
       |    }
       |  }
       |
       |  http.client.connecting-timeout = "2 seconds"
       |  http.client.parsing.max-response-reason-length = 128
       |
       |  persistence {
       |    cassandra {
       |      events-by-tag.enabled = false
       |      journal {
       |        keyspace = "fake"
       |        keyspace-autocreate = true
       |        tables-autocreate = true
       |        replication-factor = 1
       |      }
       |      snapshot {
       |        keyspace = "fake"
       |        keyspace-autocreate = true
       |        tables-autocreate = true
       |        replication-factor = 1
       |      }
       |    }
       |
       |    journal {
       |      plugin = "make-api.event-sourcing.proposals.journal"
       |    }
       |    snapshot-store {
       |      plugin = "make-api.event-sourcing.proposals.snapshot"
       |    }
       |    role = "worker"
       |  }
       |
       |  loggers = ["akka.event.slf4j.Slf4jLogger"]
       |  loglevel = "DEBUG"
       |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
       |
       |  remote {
       |    artery {
       |      enabled = on
       |      transport = tcp
       |      canonical.hostname = "localhost"
       |      canonical.port = $port
       |    }
       |    log-remote-lifecycle-events = off
       |  }
       |
       |  cluster.seed-nodes = ["akka://CassandraHealthCheckIT@localhost:$port"]
       |  cluster.jmx.multi-mbeans-in-same-jvm = on
       |
       |}
       |
       |datastax-java-driver.basic {
       |  contact-points = ["127.0.0.1:$cassandraExposedPort"]
       |  load-balancing-policy.local-datacenter = "datacenter1"
       |}
       |
       |make-api {
       |
       |  event-sourcing {
       |
       |    proposals = $${akka.persistence.cassandra}
       |    proposals {
       |
       |      journal {
       |        table = "proposal_events"
       |        metadata-table = "proposal_events_metadata"
       |        config-table = "proposal_events_config"
       |      }
       |
       |      snapshot {
       |        table = "proposal_snapshots"
       |        metadata-table = "proposal_snapshots_metadata"
       |        config-table = "proposals_snapshot_config"
       |      }
       |
       |    }
       |  }
       |  security.secure-hash-salt = "salt-secure"
       |  security.secure-vote-salt = "vote-secure"
       |  security.aes-secret-key = "secret-key"
       |}
    """.stripMargin

  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](
      Behaviors.empty,
      "CassandraHealthCheckIT",
      ConfigFactory.load(ConfigFactory.parseString(configuration))
    )
}
