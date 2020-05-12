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

import akka.actor.ActorRef
import akka.testkit.TestKit
import eu.timepit.refined.auto._
import eu.timepit.refined.scalacheck.numeric._
import org.make.api.ShardingActorTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.technical.job.JobActor.Protocol.Command._
import org.make.api.technical.job.JobActor.Protocol.Response._
import org.make.core.job.Job
import org.make.core.job.Job.JobStatus._
import org.make.core.job.Job.{JobId, Progress}
import org.scalatest.GivenWhenThen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration.{Duration, DurationInt}

class JobActorTest
    extends ShardingActorTest
    with GivenWhenThen
    with DefaultIdGeneratorComponent
    with ScalaCheckDrivenPropertyChecks {

  private val heartRate: Duration = 10.milliseconds
  private val coordinator: ActorRef = system.actorOf(JobCoordinator.props(heartRate), JobCoordinator.name)

  feature("start a job") {

    scenario("start a non-existent job") {
      val id = idGenerator.nextJobId()

      Given("an empty state")
      coordinator ! Get(id)
      expectMsg(State(None))

      And("a new job")
      coordinator ! Start(id)
      expectMsg(JobAcceptance(true))

      Then("the job is started")
      getJob(id).status should be(Running(0d))

      And("state is restored if actor is killed")
      coordinator ! Kill(id)
      Thread.sleep(10)
      getJob(id).status should be(Running(0d))
    }

    scenario("fail to start a running job") {
      val id = idGenerator.nextJobId()

      Given("a job")
      val job = startJob(id)

      Then("I cannot start it again")
      coordinator ! Start(id)
      expectMsg(JobAcceptance(false))

      And("job did not change")
      coordinator ! Get(id)
      expectMsg(State(Some(job)))
    }

    scenario("restart a stuck job") {
      val id = idGenerator.nextJobId()

      Given("a job")
      val previous = startJob(id)

      When("it is stuck")
      Thread.sleep(Job.missableHeartbeats * heartRate.toMillis)
      getJob(id).isStuck(heartRate) should be(true)

      Then("I can start it again")
      val current = startJob(id)

      And("job changed")
      assert(current.createdAt.get.isAfter(previous.createdAt.get))
      assert(current.updatedAt.get.isAfter(previous.updatedAt.get))
    }

    scenario("restart a finished job") {
      forAll { outcome: Option[Throwable] =>
        val id = idGenerator.nextJobId()

        Given("a job")
        val previous = startJob(id)

        When("job is finished")
        coordinator ! Finish(id, outcome)
        expectMsg(Ack)

        Then("I can start it again")
        val current = startJob(id)

        And("job changed")
        current match {
          case Job(_, Running(_), Some(c), Some(u))
              if c.isAfter(previous.createdAt.get) && u.isAfter(previous.updatedAt.get) =>
        }
      }
    }
  }

  feature("report progress") {

    scenario("for a non-existent job") {
      forAll { progress: Progress =>
        val id = idGenerator.nextJobId()

        When("I report some progress")
        coordinator ! Report(id, progress)

        Then("I get an error")
        expectMsg(NotRunning)
      }
    }

    scenario("for a running job") {
      forAll { progress: Progress =>
        val id = idGenerator.nextJobId()

        Given("a job")
        val _ = startJob(id)

        When("I report some progress")
        coordinator ! Report(id, progress)

        Then("progress is updated")
        getJob(id).status should be(Running(progress))
      }
    }

    scenario("for a finished job") {
      forAll { (progress: Progress, outcome: Option[Throwable]) =>
        val id = idGenerator.nextJobId()

        Given("a job")
        val _ = startJob(id)

        When("I finish it")
        coordinator ! Finish(id, outcome)
        expectMsg(Ack)

        And("I report some progress")
        coordinator ! Report(id, progress)

        Then("I get an error")
        expectMsg(NotRunning)
      }
    }
  }

  feature("heartbeat") {
    scenario("heartbeating unstucks job") {
      val id = idGenerator.nextJobId()

      Given("a job")
      val _ = startJob(id)

      When("it is stuck")
      Thread.sleep(Job.missableHeartbeats * heartRate.toMillis)
      getJob(id).isStuck(heartRate) should be(true)

      And("its heart beats")
      coordinator ! Heartbeat(id)

      Then("job is unstuck")
      getJob(id).isStuck(heartRate) should be(false)
    }
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  private def startJob(id: JobId): Job = {
    coordinator ! Start(id)
    expectMsg(JobAcceptance(true))
    getJob(id)
  }

  private def getJob(id: JobId): Job = {
    coordinator ! Get(id)
    fishForSpecificMessage() { case State(Some(job)) if job.id == id => job }
  }

}
