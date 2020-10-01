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

package org.make.api.technical.job

import akka.actor.testkit.typed.scaladsl.{FishingOutcomes, TestProbe}
import eu.timepit.refined.auto._
import eu.timepit.refined.scalacheck.numeric._
import org.make.api.ShardingTypedActorTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.technical.job.JobActor.Protocol.Command._
import org.make.api.technical.job.JobActor.Protocol.Response._
import org.make.core.job.Job
import org.make.core.job.Job.JobStatus._
import org.make.core.job.Job.{JobId, Progress}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration.{Duration, DurationInt}

class JobActorTest extends ShardingTypedActorTest with DefaultIdGeneratorComponent with ScalaCheckDrivenPropertyChecks {

  private val heartRate: Duration = 10.milliseconds
  private val coordinator = JobCoordinator(system, heartRate)

  Feature("start a job") {

    Scenario("start a non-existent job") {
      val id = idGenerator.nextJobId()
      val probe = testKit.createTestProbe[JobActor.Protocol]()

      Given("an empty state")
      coordinator ! Get(id, probe.ref)
      probe.expectMessage(State(None))

      And("a new job")
      coordinator ! Start(id, probe.ref)
      probe.expectMessage(JobAcceptance(true))

      Then("the job is started")
      getJob(id, probe).status should be(Running(0d))

      And("state is restored if actor is killed")
      coordinator ! Kill(id)
      Thread.sleep(10)
      getJob(id, probe).status should be(Running(0d))
    }

    Scenario("fail to start a running job") {
      val id = idGenerator.nextJobId()
      val probe = testKit.createTestProbe[JobActor.Protocol]()

      Given("a job")
      val job = startJob(id, probe)

      Then("I cannot start it again")
      coordinator ! Start(id, probe.ref)
      probe.expectMessage(JobAcceptance(false))

      And("job did not change")
      coordinator ! Get(id, probe.ref)
      probe.expectMessage(State(Some(job)))
    }

    Scenario("restart a stuck job") {
      val id = idGenerator.nextJobId()
      val probe = testKit.createTestProbe[JobActor.Protocol]("probe3")

      Given("a job")
      val previous = startJob(id, probe)

      When("it is stuck")
      Thread.sleep(Job.missableHeartbeats * heartRate.toMillis)
      getJob(id, probe).isStuck(heartRate) should be(true)

      Then("I can start it again")
      val current = startJob(id, probe)

      And("job changed")
      assert(current.createdAt.get.isAfter(previous.createdAt.get))
      assert(current.updatedAt.get.isAfter(previous.updatedAt.get))
    }

    Scenario("restart a finished job") {
      forAll { outcome: Option[Throwable] =>
        val id = idGenerator.nextJobId()
        val probe = testKit.createTestProbe[JobActor.Protocol]()

        Given("a job")
        val previous = startJob(id, probe)

        When("job is finished")
        coordinator ! Finish(id, outcome, probe.ref)
        probe.expectMessage(Ack)

        Then("I can start it again")
        val current = startJob(id, probe)

        And("job changed")
        current match {
          case Job(_, Running(_), Some(c), Some(u))
              if c.isAfter(previous.createdAt.get) && u.isAfter(previous.updatedAt.get) =>
        }
      }
    }
  }

  Feature("report progress") {

    Scenario("for a non-existent job") {
      forAll { progress: Progress =>
        val id = idGenerator.nextJobId()
        val probe = testKit.createTestProbe[JobActor.Protocol]()

        When("I report some progress")
        coordinator ! Report(id, progress, probe.ref)

        Then("I get an error")
        probe.expectMessage(NotRunning)
      }
    }

    Scenario("for a running job") {
      forAll { progress: Progress =>
        val id = idGenerator.nextJobId()
        val probe = testKit.createTestProbe[JobActor.Protocol]()

        Given("a job")
        val _ = startJob(id, probe)

        When("I report some progress")
        coordinator ! Report(id, progress, probe.ref)

        Then("progress is updated")
        getJob(id, probe).status should be(Running(progress))
      }
    }

    Scenario("for a finished job") {
      forAll { (progress: Progress, outcome: Option[Throwable]) =>
        val id = idGenerator.nextJobId()
        val probe = testKit.createTestProbe[JobActor.Protocol]()

        Given("a job")
        val _ = startJob(id, probe)

        When("I finish it")
        coordinator ! Finish(id, outcome, probe.ref)
        probe.expectMessage(Ack)

        And("I report some progress")
        coordinator ! Report(id, progress, probe.ref)

        Then("I get an error")
        probe.expectMessage(NotRunning)
      }
    }
  }

  Feature("heartbeat") {
    Scenario("heartbeating unstucks job") {
      val id = idGenerator.nextJobId()
      val probe = testKit.createTestProbe[JobActor.Protocol]()

      Given("a job")
      val _ = startJob(id, probe)

      When("it is stuck")
      Thread.sleep(Job.missableHeartbeats * heartRate.toMillis)
      getJob(id, probe).isStuck(heartRate) should be(true)

      And("its heart beats")
      coordinator ! Heartbeat(id, probe.ref)

      Then("job is unstuck")
      getJob(id, probe).isStuck(heartRate) should be(false)
    }
  }

  private def startJob(id: JobId, probe: TestProbe[JobActor.Protocol]): Job = {
    coordinator ! Start(id, probe.ref)
    probe.expectMessage(JobAcceptance(true))
    getJob(id, probe)
  }

  private def getJob(id: JobId, probe: TestProbe[JobActor.Protocol]): Job = {
    coordinator ! Get(id, probe.ref)
    probe.fishForMessage(5.seconds) {
      case State(Some(job)) if job.id == id => FishingOutcomes.complete
      case _                                => FishingOutcomes.continueAndIgnore
    } match {
      case Seq(State(Some(job))) => job
    }
  }

}
