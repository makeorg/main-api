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

package org.make.api.sequence

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.core.proposal.ProposalId
import org.make.core.reference.{Country, Language}
import org.make.core.sequence._
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.Mockito
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.GivenWhenThen
import org.scalatest.mockito.MockitoSugar

class SequenceActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging {

  val THREAD_SLEEP_MICROSECONDS: Int = 100
  val dateHelper: DateHelper = MockitoSugar.mock[DateHelper]
  val coordinator: ActorRef =
    system.actorOf(SequenceCoordinator.props(dateHelper), SequenceCoordinator.name)
  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]"))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2017-06-02T12:30:40Z[UTC]"))

  val user: User = User(
    userId = mainUserId,
    email = "john.snow@the-night-watch.com",
    firstName = None,
    lastName = None,
    lastIp = None,
    hashedPassword = None,
    emailVerified = true,
    enabled = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    resetToken = None,
    verificationTokenExpiresAt = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = None
  )

  private def sequence(sequenceId: SequenceId) = Sequence(
    sequenceTranslation = Seq(),
    sequenceId = sequenceId,
    title = "This is a sequence",
    createdAt = mainCreatedAt,
    updatedAt = mainCreatedAt,
    slug = "this-is-a-sequence",
    creationContext = RequestContext.empty,
    proposalIds = Seq(),
    themeIds = Seq(),
    status = SequenceStatus.Unpublished,
    events = List(
      SequenceAction(
        date = mainCreatedAt.get,
        user = mainUserId,
        actionType = "create",
        arguments = Map("title" -> "This is a sequence", "themeIds" -> "")
      )
    ),
    searchable = false
  )

  var dateStubNow: OngoingStubbing[ZonedDateTime] = Mockito.when(dateHelper.now())

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  feature("Create a sequence") {

    dateStubNow = dateStubNow.thenReturn(mainCreatedAt.get)

    val sequenceId: SequenceId = SequenceId("createCommand")
    scenario("Initialize the state if it was empty") {

      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      And("a newly created Sequence")
      coordinator ! CreateSequenceCommand(
        sequenceId = sequenceId,
        slug = "this-is-a-sequence",
        requestContext = RequestContext.empty,
        moderatorId = user.userId,
        title = "This is a sequence",
        status = SequenceStatus.Unpublished,
        searchable = false
      )

      expectMsg(sequenceId)

      Then("have the sequence state after sequence")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(Some(sequence(sequenceId)))

      And("recover its state after having been kill")
      coordinator ! KillSequenceShard(sequenceId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(Some(sequence(sequenceId)))

    }
  }

  feature("Update a sequence") {

    dateStubNow = dateStubNow
      .thenReturn(mainUpdatedAt.get)
      .thenReturn(mainCreatedAt.get)
      .thenReturn(mainUpdatedAt.get)

    val sequenceId: SequenceId = SequenceId("createUpdateCommand")

    scenario("Fail if SequenceId doesn't exists") {
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      When("a asking for a fake SequenceId")
      coordinator ! UpdateSequenceCommand(
        sequenceId = SequenceId("fake"),
        requestContext = RequestContext.empty,
        moderatorId = user.userId,
        title = Some("An updated content"),
        status = Some(SequenceStatus.Published),
        operationId = None,
        themeIds = Seq.empty
      )

      Then("returns None")
      expectMsg(None)
    }

    scenario("Change the state and create a snapshot if valid") {
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      And("a newly proposed Sequence")
      coordinator ! CreateSequenceCommand(
        sequenceId = sequenceId,
        slug = "this-is-a-sequence",
        requestContext = RequestContext.empty,
        moderatorId = user.userId,
        title = "This is a sequence",
        status = SequenceStatus.Published,
        searchable = false
      )

      expectMsg(sequenceId)

      When("updating this Sequence")
      coordinator ! UpdateSequenceCommand(
        sequenceId = sequenceId,
        moderatorId = user.userId,
        requestContext = RequestContext.empty,
        title = Some("An updated content"),
        operationId = None,
        status = Some(SequenceStatus.Published),
        themeIds = Seq.empty
      )

      val modified = Some(
        sequence(sequenceId)
          .copy(
            title = "An updated content",
            slug = "an-updated-content",
            updatedAt = mainUpdatedAt,
            status = SequenceStatus.Published
          )
      )
      expectMsg(modified)

      Then("getting its updated state after update")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(modified)

      And("recover its updated state after having been kill")
      coordinator ! KillSequenceShard(sequenceId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)
      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(modified)
    }
  }

  feature("View a sequence") {
    dateStubNow = dateStubNow
      .thenReturn(mainUpdatedAt.get)
      .thenReturn(mainCreatedAt.get)
      .thenReturn(mainUpdatedAt.get)

    val sequenceId: SequenceId = SequenceId("viewCommand")

    scenario("Fail if SequenceId doesn't exists") {
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      When("a asking for a fake SequenceId")
      coordinator ! ViewSequenceCommand(SequenceId("fake"), RequestContext.empty)

      Then("returns None")
      expectMsg(None)
    }

    scenario("Return the state if valid") {
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      When("a new Sequence is proposed")
      coordinator ! CreateSequenceCommand(
        sequenceId = sequenceId,
        slug = "this-is-a-sequence",
        requestContext = RequestContext.empty,
        moderatorId = user.userId,
        title = "This is a sequence",
        status = SequenceStatus.Unpublished,
        searchable = false
      )

      expectMsg(sequenceId)

      Then("returns the state")
      coordinator ! ViewSequenceCommand(sequenceId, RequestContext.empty)
      expectMsg(Some(sequence(sequenceId)))
    }
  }

  feature("Add proposals to a sequence") {
    dateStubNow = dateStubNow
      .thenReturn(mainUpdatedAt.get)
      .thenReturn(mainCreatedAt.get)
      .thenReturn(mainUpdatedAt.get)

    val sequenceId: SequenceId = SequenceId("addProposalsCommand")
    scenario("Fail if SequenceId doesn't exists") {
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      When("I add some proposals")
      coordinator ! AddProposalsSequenceCommand(
        moderatorId = user.userId,
        sequenceId = SequenceId("fakeSequenceAddProposal"),
        requestContext = RequestContext.empty,
        proposalIds = Seq(ProposalId("1234"), ProposalId("5678"))
      )

      Then("returns None")
      expectMsg(None)
    }

    scenario("Add proposals in a sequence and ensure a snapshot is created if valid") {
      val sequenceId: SequenceId = SequenceId("addProposalsCommandSnapshot")
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)
      expectMsg(None)

      And("a newly Sequence")
      coordinator ! CreateSequenceCommand(
        sequenceId = sequenceId,
        slug = "this-is-a-sequence",
        requestContext = RequestContext.empty,
        moderatorId = user.userId,
        title = "This is a sequence",
        status = SequenceStatus.Unpublished,
        searchable = false
      )

      expectMsg(sequenceId)

      When("adding some proposals to Sequence")
      coordinator ! AddProposalsSequenceCommand(
        moderatorId = user.userId,
        sequenceId = sequenceId,
        requestContext = RequestContext.empty,
        proposalIds = Seq(ProposalId("1234"), ProposalId("5678"))
      )

      val modified = Some(
        sequence(sequenceId)
          .copy(proposalIds = Seq(ProposalId("1234"), ProposalId("5678")), updatedAt = mainUpdatedAt)
      )
      expectMsg(modified)

      Then("getting its updated state after update")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(modified)

      And("recover its updated state after having been kill")
      coordinator ! KillSequenceShard(sequenceId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(modified)
    }
  }

  feature("Remove proposals in a sequence") {

    dateStubNow = dateStubNow
      .thenReturn(mainCreatedAt.get)
      .thenReturn(mainUpdatedAt.get)
      .thenReturn(mainUpdatedAt.get)

    scenario("Remove proposals in a sequence") {
      val sequenceId: SequenceId = SequenceId("addProposalsCommandNonEmptySnapshot")
      Given("an empty state")
      coordinator ! GetSequence(sequenceId, RequestContext.empty)

      expectMsg(None)

      And("a newly Sequence")
      coordinator ! CreateSequenceCommand(
        sequenceId = sequenceId,
        slug = "this-is-a-sequence",
        requestContext = RequestContext.empty,
        moderatorId = user.userId,
        title = "This is a sequence",
        status = SequenceStatus.Published,
        searchable = false
      )

      expectMsg(sequenceId)

      When("adding proposals: ProposalId('1234') and ProposalId('5678')")
      coordinator ! AddProposalsSequenceCommand(
        moderatorId = user.userId,
        sequenceId = sequenceId,
        requestContext = RequestContext.empty,
        proposalIds = Seq(ProposalId("1234"), ProposalId("5678"))
      )

      val modified = Some(
        sequence(sequenceId)
          .copy(proposalIds = Seq(ProposalId("1234"), ProposalId("5678")), updatedAt = mainUpdatedAt)
      )

      expectMsg(modified)

      And("remove proposals: ProposalId('1234')")
      coordinator ! RemoveProposalsSequenceCommand(
        moderatorId = user.userId,
        sequenceId = sequenceId,
        requestContext = RequestContext.empty,
        proposalIds = Seq(ProposalId("1234"))
      )

      val modified2 = Some(
        sequence(sequenceId)
          .copy(proposalIds = Seq(ProposalId("5678")), updatedAt = mainUpdatedAt)
      )

      expectMsg(modified2)
    }
  }
}
