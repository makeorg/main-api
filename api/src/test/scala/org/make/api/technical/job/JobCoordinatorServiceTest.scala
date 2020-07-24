/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.job

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestKit
import akka.util.Timeout
import eu.timepit.refined.auto._
import eu.timepit.refined.scalacheck.numeric._
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.job.JobReportingActor.JobReportingActorFacade
import org.make.api.technical.{DefaultIdGeneratorComponent, TimeSettings}
import org.make.api.{ActorSystemComponent, MakeBackoffSupervisor, ShardingActorTest}
import org.make.core.job.Job.JobStatus._
import org.make.core.job.Job.{JobId, Progress}
import org.scalatest.concurrent.Eventually.eventually
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
import scala.util.Success

class JobCoordinatorServiceTest
    extends ShardingActorTest
    with DefaultJobCoordinatorServiceComponent
    with DefaultIdGeneratorComponent
    with JobCoordinatorComponent
    with ActorSystemComponent
    with ScalaCheckDrivenPropertyChecks {

  val heartRate: FiniteDuration = 10.milliseconds

  override val actorSystem: ActorSystem = system

  override lazy val jobCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / s"${JobCoordinator.name}-backoff")
      .resolveOne(),
    atMost = timeout.duration
  )

  locally {
    val (props, name) = MakeBackoffSupervisor.propsAndName(JobCoordinator.props(heartRate), JobCoordinator.name)
    actorSystem.actorOf(props, name)
  }

  private implicit val timeout: Timeout = TimeSettings.defaultTimeout

  Feature("Job monitoring") {
    Scenario("it works") {
      forAll { (uuid: UUID, progress: Progress, outcome: Option[Exception]) =>
        val id = JobId(uuid.toString)

        val work = new JobCoordinatorServiceTest.WorkHelper

        Given("a job")
        val started = jobCoordinatorService.start(id, heartRate) { report =>
          work.report = report
          work.future
        }

        When("it is successfully started")
        started.will(be(JobAcceptance(true)))
        eventually {
          work.report should not be (null)
        }

        Then("it is running")
        jobCoordinatorService.get(id).map(_.map(_.status)).will(be(Some(Running(0d))))

        And("it should progress")
        work.report(progress).will(be(()))
        jobCoordinatorService.get(id).map(_.map(_.status)).will(be(Some(Running(progress))))

        And("its heart should beat")
        eventually {
          jobCoordinatorService.get(id).map(_.map(_.isStuck(heartRate))).will(be(Some(false)))
        }

        And("it should finish")
        work.complete(outcome)
        eventually {
          jobCoordinatorService
            .get(id)
            .map(_.map(_.status))
            .will(be(Some(Finished(outcome.flatMap(e => Option(e.getMessage))))))
        }
      }
    }
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

}

object JobCoordinatorServiceTest {

  class WorkHelper() {

    var report: JobReportingActorFacade = null

    private val promise = Promise[Unit]()
    val future: Future[Unit] = promise.future

    def complete(outcome: Option[Exception]): Unit = outcome match {
      case None        => promise.complete(Success(()))
      case Some(error) => promise.failure(error)
    }

  }

}
