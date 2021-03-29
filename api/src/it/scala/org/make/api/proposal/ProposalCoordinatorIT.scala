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

package org.make.api.proposal

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.{ActorSystem => ClassicActorSystem}
import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.docker.DockerCassandraService
import org.make.api.extensions.DefaultMakeSettingsComponent
import org.make.api.sessionhistory.{
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCoordinatorComponent
}
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.{ActorSystemComponent, ActorSystemTypedComponent, ItMakeTest}
import org.make.core.RequestContext
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZonedDateTime
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ProposalCoordinatorIT
    extends ScalaTestWithActorTestKit(ActorTestKit(ProposalCoordinatorIT.actorSystem))
    with ItMakeTest
    with ActorSystemComponent
    with ActorSystemTypedComponent
    with DefaultIdGeneratorComponent
    with DefaultProposalCoordinatorServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DockerCassandraService
    with DefaultMakeSettingsComponent
    with ProposalCoordinatorComponent
    with SessionHistoryCoordinatorComponent {

  override implicit val actorSystemTyped: ActorSystem[_] = system
  override implicit val actorSystem: ClassicActorSystem = system.classicSystem
  override val cassandraExposedPort: Int = ProposalCoordinatorIT.cassandraExposedPort
  override val proposalCoordinator: ActorRef[ProposalCommand] =
    ProposalCoordinator(system, sessionHistoryCoordinatorService, 1.second, idGenerator)
  override def sessionHistoryCoordinator: actor.ActorRef = ???

  Feature("delete a proposal") {

    Scenario("it works") {

      val proposalId = Await.result(
        proposalCoordinatorService.propose(
          idGenerator.nextProposalId(),
          RequestContext.empty,
          user(idGenerator.nextUserId()),
          ZonedDateTime.now(),
          "deleteme",
          question(idGenerator.nextQuestionId()),
          false
        ),
        30.seconds
      )

      whenReady(proposalCoordinatorService.getProposal(proposalId), Timeout(30.seconds)) { _ shouldBe defined }
      whenReady(
        proposalCoordinatorService
          .delete(proposalId, RequestContext.empty)
          .flatMap(_ => proposalCoordinatorService.getProposal(proposalId)),
        Timeout(30.seconds)
      ) { _ should not be defined }

    }

  }

}

object ProposalCoordinatorIT {
  val cassandraExposedPort: Int = 15002
  val port: Int = 15102
  val configuration: String =
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://ProposalCoordinatorIT@localhost:$port"]
       |  cluster.jmx.multi-mbeans-in-same-jvm = on
       |
       |  persistence {
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
       |  remote.artery.canonical {
       |    port = $port
       |    hostname = "localhost"
       |  }
       |
       |  test {
       |    timefactor = 10.0
       |  }
       |}
       |
       |datastax-java-driver.basic {
       |  contact-points = ["127.0.0.1:$cassandraExposedPort"]
       |  load-balancing-policy.local-datacenter = "datacenter1"
       |}
       |
       |make-api {
       |  event-sourcing.proposals.events-by-tag.enabled = true
       |  kafka {
       |    connection-string = "nowhere:-1"
       |    schema-registry = "http://nowhere:-1"
       |  }
       |
       |  cookie-session.lifetime = "600 milliseconds"
       |}
    """.stripMargin

  val fullConfiguration: Config =
    ConfigFactory
      .parseString(configuration)
      .withFallback(ConfigFactory.load("default-application.conf"))
      .resolve()

  val actorSystem = ClassicActorSystem("ProposalCoordinatorIT", fullConfiguration).toTyped

}
