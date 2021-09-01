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

import akka.actor.{ActorRef, ActorSystem}
import akka.actor.typed.{ActorRef => TypedActorRef}
import akka.actor.typed.scaladsl.adapter._
import akka.testkit.{ImplicitSender, TestProbe}
import akka.util.Timeout
import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.make.api.docker.DockerCassandraService
import org.make.api.proposal.{ProposalCommand, ProposalCoordinator, ProposeCommand}
import org.make.api.proposal.ProposalActorResponse.Envelope
import org.make.api.sessionhistory.SessionHistoryCoordinatorService
import org.make.api.technical.{DefaultIdGeneratorComponent, TimeSettings}
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus
import org.make.api.{ItMakeTest, TestUtilsIT}
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class CassandraHealthCheckActorIT
    extends TestProbe(CassandraHealthCheckActorIT.actorSystem)
    with DefaultIdGeneratorComponent
    with ItMakeTest
    with ImplicitSender
    with DockerCassandraService {

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  override val cassandraExposedPort: Int = CassandraHealthCheckActorIT.cassandraExposedPort
  implicit val timeout: Timeout = TimeSettings.defaultTimeout.duration * 3

  val LOCK_DURATION_MILLISECONDS: FiniteDuration = 42.milliseconds

  val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService = mock[SessionHistoryCoordinatorService]

  val coordinator: TypedActorRef[ProposalCommand] =
    ProposalCoordinator(system.toTyped, sessionHistoryCoordinatorService, LOCK_DURATION_MILLISECONDS, idGenerator)

  Feature("Check Cassandra status") {
    Scenario("query proposal journal") {
      Given("a cassandra health check actor")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckCassandra: ActorRef = actorSystem.actorOf(
        CassandraHealthCheckActor.props(healthCheckExecutionContext),
        CassandraHealthCheckActor.name
      )
      And("a proposed proposal")
      val proposalId = ProposalId("fake-proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = TestUtilsIT.user(id = UserId("fake-user"), email = "fake@user.com", firstName = None, lastName = None),
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
        ref
      )

      expectMsgType[Envelope[ProposalId]](1.minute)

      When("I send a message to check the status of cassandra")
      healthCheckCassandra ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("cassandra", "OK"))
    }
  }

}

object CassandraHealthCheckActorIT {
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
       |  cluster.seed-nodes = ["akka://CassandraHealthCheckActorIT@localhost:$port"]
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
       |  security.aes-initial-vector = "initial-vector"
       |  security.aes-secret-key = "secret-key"
       |}
    """.stripMargin

  val actorSystem =
    ActorSystem("CassandraHealthCheckActorIT", ConfigFactory.load(ConfigFactory.parseString(configuration)))

}
