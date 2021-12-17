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

package org.make.api.userhistory

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import enumeratum.values.scalacheck._
import org.make.api.{ActorTest, MakeUnitTest}
import org.make.api.docker.DockerCassandraService
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{ActorSystemComponent, DefaultIdGeneratorComponent}
import org.make.core.{DateHelper, RequestContext}
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.proposal.VoteKey
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.rng.Seed
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class UserHistoryCoordinatorIT
    extends ActorTest(UserHistoryCoordinatorIT.actorSystem)
    with MakeUnitTest
    with DefaultIdGeneratorComponent
    with DefaultUserHistoryCoordinatorServiceComponent
    with DockerCassandraService
    with MakeSettingsComponent
    with ActorSystemComponent
    with UserHistoryCoordinatorComponent {

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  override val cassandraExposedPort: Int = UserHistoryCoordinatorIT.cassandraExposedPort
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val userHistoryCoordinator: ActorRef[UserHistoryCommand] = UserHistoryCoordinator(actorSystem)

  Feature("delete a user") {

    Scenario("it deletes their history") {

      val userVotes: Seq[UserVote] = {
        val userVoteGen = arbitrary[VoteKey].map(UserVote(idGenerator.nextProposalId(), _, Trusted))
        Gen.nonEmptyListOf(userVoteGen).pureApply(Gen.Parameters.default, Seed.random())
      }
      val userId = idGenerator.nextUserId()

      val futureHasVoted = Source(userVotes)
        .mapAsync(5) { userVote =>
          userHistoryCoordinatorService.logTransactionalHistory(
            LogUserVoteEvent(
              userId,
              RequestContext.empty,
              UserAction[UserVote](date = DateHelper.now(), actionType = "vote", arguments = userVote)
            )
          )
        }
        .runWith(Sink.ignore)
      whenReady(futureHasVoted, Timeout(30.seconds))(_ => ())

      whenReady(
        userHistoryCoordinatorService
          .retrieveVoteAndQualifications(userId = userId, proposalIds = userVotes.map(_.proposalId)),
        Timeout(30.seconds)
      )(_.size shouldBe userVotes.size)

      whenReady(
        userHistoryCoordinatorService
          .delete(userId)
          .flatMap(
            _ =>
              userHistoryCoordinatorService
                .retrieveVoteAndQualifications(userId = userId, proposalIds = userVotes.map(_.proposalId))
          ),
        Timeout(30.seconds)
      )(_ shouldBe empty)

    }

  }

}

object UserHistoryCoordinatorIT {
  val cassandraExposedPort: Int = 15003
  val port: Int = 15103
  val configuration: String =
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://UserHistoryCoordinatorIT@localhost:$port"]
       |  cluster.jmx.multi-mbeans-in-same-jvm = on
       |
       |  persistence {
       |    journal {
       |      plugin = "make-api.event-sourcing.technical.journal"
       |    }
       |    snapshot-store {
       |      plugin = "make-api.event-sourcing.technical.snapshot"
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
       |  event-sourcing {
       |    users {
       |      events-by-tag.enabled = true
       |    }
       |  }
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

  val actorSystem: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty, "UserHistoryCoordinatorIT", fullConfiguration)

}
