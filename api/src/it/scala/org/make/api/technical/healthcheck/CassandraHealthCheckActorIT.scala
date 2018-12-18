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
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.make.api.ItMakeTest
import org.make.api.docker.DockerCassandraService
import org.make.api.proposal.{ProposalCoordinator, ProposeCommand}
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CassandraHealthCheckActorIT
    extends TestKit(CassandraHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with DockerCassandraService
    with ItMakeTest {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
    system.terminate()
  }

  override val cassandraExposedPort: Int = CassandraHealthCheckActorIT.cassandraExposedPort
  implicit val timeout: Timeout = TimeSettings.defaultTimeout.duration * 3

  val sessionHistoryActor: TestProbe = TestProbe()
  val sessionHistoryCoordinator: TestProbe = TestProbe()
  val coordinator: ActorRef =
    system.actorOf(ProposalCoordinator.props(sessionHistoryActor = sessionHistoryActor.ref), ProposalCoordinator.name)

  feature("Check Cassandra status") {
    scenario("query proposal journal") {
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
        user = User(
          userId = UserId("fake-user"),
          email = "fake@user.com",
          firstName = None,
          lastName = None,
          lastIp = None,
          hashedPassword = None,
          enabled = true,
          emailVerified = true,
          lastConnection = DateHelper.now(),
          verificationToken = None,
          resetToken = None,
          verificationTokenExpiresAt = None,
          resetTokenExpiresAt = None,
          roles = Seq(RoleCitizen),
          country = Country("FR"),
          language = Language("fr"),
          profile = None,
          lastMailingError = None
        ),
        createdAt = DateHelper.now(),
        content = "This is a proposal",
        question = Question(
          QuestionId("fake-question"),
          "fake-question",
          country = Country("FR"),
          language = Language("fr"),
          question = "fake question",
          None,
          None
        ),
        initialProposal = false
      )

      expectMsgType[ProposalId](1.minute)

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
       |    journal {
       |      plugin = "make-api.event-sourcing.proposals.read-journal"
       |    }
       |    snapshot-store {
       |      plugin = "make-api.event-sourcing.proposals.snapshot-store"
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
       |make-api {
       |
       |  event-sourcing {
       |    proposals {
       |
       |      read-journal = $${cassandra-journal}
       |      read-journal {
       |        port = $cassandraExposedPort
       |        keyspace = "fake"
       |        query-plugin = "make-api.event-sourcing.proposals.query-journal"
       |        table = "proposal_events"
       |        metadata-table = "proposal_events_metadata"
       |        config-table = "proposal_events_config"
       |        events-by-tag.table = "proposal_tag_views"
       |      }
       |
       |      snapshot-store = $${cassandra-snapshot-store}
       |      snapshot-store {
       |        port = $cassandraExposedPort
       |        keyspace = "fake"
       |        table = "proposal_snapshots"
       |        metadata-table = "proposal_snapshots_metadata"
       |        config-table = "proposals_snapshot_config"
       |      }
       |
       |      query-journal = $${cassandra-query-journal}
       |      query-journal {
       |        write-plugin = "make-api.event-sourcing.proposals.read-journal"
       |      }
       |    }
       |  }
       |}
    """.stripMargin

  val actorSystem =
    ActorSystem("CassandraHealthCheckActorIT", ConfigFactory.load(ConfigFactory.parseString(configuration)))

}
