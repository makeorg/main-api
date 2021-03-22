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

import java.time.ZonedDateTime
import akka.actor.typed.ActorRef
import cats.data.NonEmptyList
import org.make.api.sessionhistory.{SessionHistoryCoordinatorService, TransactionalSessionHistoryEvent}
import org.make.api.{ShardingTypedActorTest, TestUtils}
import org.make.api.proposal.ProposalActorResponse._
import org.make.api.proposal.ProposalActorResponse.Error._
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.history.HistoryActions._
import org.make.core.history.HistoryActions.VoteTrust._
import org.make.core.idea.IdeaId
import org.make.core.operation.{Operation, OperationId, OperationKind, OperationStatus}
import org.make.core.proposal.ProposalActionType.ProposalProposeAction
import org.make.core.proposal.ProposalStatus.{Accepted, Pending, Postponed, Refused}
import org.make.core.proposal.QualificationKey.LikeIt
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}
import org.make.core.{ApplicationName, DateHelper, RequestContext}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class ProposalActorTest
    extends ShardingTypedActorTest
    with ScalaCheckDrivenPropertyChecks
    with DefaultIdGeneratorComponent {

  val questionOnNothingFr = Question(
    questionId = QuestionId("my-question"),
    slug = "my-question",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "some unsolved question",
    shortTitle = None,
    operationId = None
  )

  val questionOnTheme = Question(
    questionId = QuestionId("my-question"),
    slug = "my-question",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "some unsolved question",
    shortTitle = None,
    operationId = None
  )

  val questionOnNothingIT = Question(
    questionId = QuestionId("my-italian-question"),
    slug = "my-question",
    countries = NonEmptyList.of(Country("IT")),
    language = Language("it"),
    question = "some unsolved question",
    shortTitle = None,
    operationId = None
  )

  val operation1: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("operation1"),
    slug = "operation-1",
    operationKind = OperationKind.BusinessConsultation,
    events = List.empty,
    questions = Seq.empty,
    createdAt = None,
    updatedAt = None
  )
  val operation2: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("operation2"),
    slug = "operation-2",
    operationKind = OperationKind.BusinessConsultation,
    events = List.empty,
    questions = Seq.empty,
    createdAt = None,
    updatedAt = None
  )

  val CREATED_DATE_SECOND_MINUS: Int = 10
  val THREAD_SLEEP_MICROSECONDS: Int = 100
  val LOCK_DURATION_MILLISECONDS: FiniteDuration = 42.milliseconds

  val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService = mock[SessionHistoryCoordinatorService]
  when(
    sessionHistoryCoordinatorService
      .logTransactionalHistory(any[TransactionalSessionHistoryEvent[_]])
  ).thenReturn(Future.unit)

  val coordinator: ActorRef[ProposalCommand] =
    ProposalCoordinator(system, sessionHistoryCoordinatorService, LOCK_DURATION_MILLISECONDS, idGenerator)

  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: Option[ZonedDateTime] = Some(DateHelper.now().minusSeconds(CREATED_DATE_SECOND_MINUS))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(DateHelper.now())

  val user: User = TestUtils.user(id = mainUserId)

  private def newProposal(proposalId: ProposalId, content: String, question: Question) =
    TestUtils.proposal(
      id = proposalId,
      author = mainUserId,
      createdAt = mainCreatedAt,
      updatedAt = None,
      status = Pending,
      content = content,
      questionId = question.questionId,
      operationId = question.operationId,
      events = List(
        ProposalAction(
          date = mainCreatedAt.get,
          user = mainUserId,
          actionType = ProposalProposeAction.value,
          arguments = Map("content" -> content)
        )
      )
    )

  Feature("Propose a proposal") {
    val proposalId: ProposalId = ProposalId("proposeCommand")
    val proposalItalyId: ProposalId = ProposalId("proposeItalyCommand")
    Scenario("Initialize the state if it was empty") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)
      probe.expectMessage(ProposalNotFound)

      And("a newly proposed Proposal")

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      probe.expectMessage(
        Envelope(newProposal(proposalId = proposalId, content = "This is a proposal", question = questionOnNothingFr))
      )

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      probe.expectMessage(
        Envelope(newProposal(proposalId = proposalId, content = "This is a proposal", question = questionOnNothingFr))
      )
    }

    Scenario("Initialize the state for a proposal from Italy") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an empty state")
      coordinator ! GetProposal(proposalItalyId, RequestContext.empty, probe.ref)
      probe.expectMessage(ProposalNotFound)

      And("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalItalyId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an italian proposal",
        question = questionOnNothingIT,
        initialProposal = false,
        probe.ref
      )

      probe.expectMessage(Envelope(proposalItalyId))

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalItalyId, RequestContext.empty, probe.ref)

      probe.expectMessage(
        Envelope(
          newProposal(
            proposalId = proposalItalyId,
            content = "This is an italian proposal",
            question = questionOnNothingIT
          )
        )
      )

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalItalyId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetProposal(proposalItalyId, RequestContext.empty, probe.ref)

      probe.expectMessage(
        Envelope(
          newProposal(
            proposalId = proposalItalyId,
            content = "This is an italian proposal",
            question = questionOnNothingIT
          )
        )
      )
    }
  }

  Feature("View a proposal") {
    val proposalId: ProposalId = ProposalId("viewCommand")
    Scenario("Fail if ProposalId doesn't exists") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)
      probe.expectMessage(ProposalNotFound)

      When("a asking for a fake ProposalId")
      coordinator ! ViewProposalCommand(ProposalId("fake"), RequestContext.empty, probe.ref)

      Then("returns None")
      probe.expectMessage(ProposalNotFound)
    }

    Scenario("Return the state if valid") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)
      probe.expectMessage(ProposalNotFound)

      When("a new Proposal is proposed")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      Then("returns the state")
      coordinator ! ViewProposalCommand(proposalId, RequestContext.empty, probe.ref)
      probe.expectMessage(
        Envelope(newProposal(proposalId = proposalId, content = "This is a proposal", question = questionOnNothingFr))
      )
    }
  }

  Feature("accept a proposal") {
    Scenario("accepting a non existing proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("no proposal corresponding to id 'nothing-there'")
      When("I try to accept the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("nothing-there"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(),
        tags = Seq(TagId("some tag id")),
        question = questionOnTheme,
        idea = Some(IdeaId("my-id")),
        replyTo = probe.ref
      )

      Then("I should receive 'None' since nothing is found")
      probe.expectMessage(ProposalNotFound)

    }

    Scenario("accept an existing proposal changing the text") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-changing-text"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnTheme,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-moderated-changing-text")))

      When("I validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-changing-text"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = Some("This content must be changed"),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        question = questionOnTheme,
        idea = Some(IdeaId("my-idea")),
        replyTo = probe.ref
      )

      Then("I should receive the accepted proposal with modified content")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("to-be-moderated-changing-text"))
      response.events.length should be(2)
      response.content should be("This content must be changed")
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.idea should be(Some(IdeaId("my-idea")))

    }

    Scenario("accept an existing proposal without changing text") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-moderated")))

      When("I validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        question = questionOnNothingFr,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("some-idea")),
        replyTo = probe.ref
      )

      Then("I should receive the accepted proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("to-be-moderated"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.idea should be(Some(IdeaId("some-idea")))
    }

    Scenario("accept an existing proposal without a theme") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      val proposalId = ProposalId("to-be-moderated-without-a-theme")
      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        question = questionOnNothingFr,
        labels = Seq.empty,
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("some-idea")),
        replyTo = probe.ref
      )

      Then("I should receive the accepted proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(proposalId)
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq.empty)
      response.idea should be(Some(IdeaId("some-idea")))
    }

    Scenario("validating a validated proposal shouldn't do anything") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a validated proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-2"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-moderated-2")))

      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("some-idea")),
        question = questionOnNothingFr,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]].value

      When("I re-validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some other user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = Some("something different"),
        labels = Seq(LabelId("action2")),
        tags = Seq(TagId("some tag id 2")),
        idea = Some(IdeaId("some-idea")),
        question = questionOnNothingFr,
        replyTo = probe.ref
      )

      Then("I should receive an error")
      val error = probe.expectMessageType[InvalidStateError]
      error.message should be("Proposal to-be-moderated-2 is already validated")
    }

    Scenario("validate a proposal and set the idea") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a validated proposal")
      val proposalId = ProposalId("to-be-moderated-with-idea-1")
      val idea = IdeaId("idea-1")
      val originalContent = "This is a proposal that will be validated with idea"
      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(idea),
        question = questionOnTheme,
        replyTo = probe.ref
      )
      val validatedProposal: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      Then("I should have an idea present")
      validatedProposal.proposalId should be(proposalId)
      validatedProposal.idea should be(Some(idea))
      validatedProposal.questionId should be(Some(questionOnTheme.questionId))

      When("I search for the proposal")
      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)
      val searchedProposal: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      Then("I should have idea present")
      searchedProposal.proposalId should be(proposalId)
      searchedProposal.idea should be(Some(idea))
    }
  }

  Feature("refuse a proposal") {
    Scenario("refusing a non existing proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("no proposal corresponding to id 'nothing-there'")
      When("I try to refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("nothing-there"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("nothing"),
        replyTo = probe.ref
      )

      Then("I should receive 'None' since nothing is found")
      probe.expectMessage(ProposalNotFound)
    }

    Scenario("refuse an existing proposal with a refuse reason") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be refused with a reason"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-moderated")))

      When("I refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("this proposal is bad"),
        replyTo = probe.ref
      )

      Then("I should receive the refused proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("to-be-moderated"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Refused)
      response.refusalReason should be(Some("this proposal is bad"))
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
    }

    Scenario("refuse an existing proposal without a refuse reason") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be refused without reason"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-moderated")))

      When("I refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = None,
        replyTo = probe.ref
      )

      Then("I should receive the refused proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("to-be-moderated"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Refused)
      response.refusalReason should be(None)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
    }

    Scenario("refusing a refused proposal shouldn't do anything") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a refused proposal")
      val originalContent = "This is a new proposal"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-2"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-moderated-2")))

      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("my reason"),
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      When("I re-refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some other user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("another reason"),
        replyTo = probe.ref
      )

      Then("I should receive an error")
      val error = probe.expectMessageType[InvalidStateError]
      error.message should be("Proposal to-be-moderated-2 is already refused")
    }
  }

  Feature("postpone a proposal") {
    Scenario("postpone a non existing proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("no proposal corresponding to id 'nothing-there'")
      When("I try to postpone the proposal")
      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("nothing-there"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("I should receive 'None' since nothing is found")
      probe.expectMessage(ProposalNotFound)
    }

    Scenario("postpone a pending proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be postponed"
      coordinator ! ProposeCommand(
        ProposalId("to-be-postponed"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("to-be-postponed")))

      When("i postpone the proposal")
      coordinator ! PostponeProposalCommand(
        moderator = UserId("moderatorFoo"),
        proposalId = ProposalId("to-be-postponed"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("I should receive the postponed proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("to-be-postponed"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Postponed)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
    }

    Scenario("postpone a refused proposal shouldn't do nothing") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a refused proposal")
      val originalContent = "This is a new proposal to refuse"
      coordinator ! ProposeCommand(
        ProposalId("proposal-to-be-refused"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("proposal-to-be-refused")))

      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("proposal-to-be-refused"),
        moderator = UserId("fooUser"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("good reason"),
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      When("I try to postpone the proposal")
      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("proposal-to-be-refused"),
        moderator = UserId("moderatorFoo"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("I should receive an error")
      val error = probe.expectMessageType[InvalidStateError]
      error.message should be("Proposal proposal-to-be-refused is already moderated and cannot be postponed")
    }

    Scenario("postpone a postponed proposal shouldn't do nothing") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a postponed proposal")
      val originalContent = "This is a new proposal to postpone"
      coordinator ! ProposeCommand(
        ProposalId("proposal-to-be-postponed"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(ProposalId("proposal-to-be-postponed")))

      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("proposal-to-be-postponed"),
        moderator = UserId("fooUser"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      When("I try to re-postpone the proposal")
      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("proposal-to-be-postponed"),
        moderator = UserId("moderatorFoo"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("I should receive an error")
      val error = probe.expectMessageType[InvalidStateError]
      error.message should be("Proposal proposal-to-be-postponed is already postponed")
    }
  }

  Feature("Update a proposal") {
    val proposalId: ProposalId = ProposalId("updateCommand")
    Scenario("Fail if ProposalId doesn't exists") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)
      probe.expectMessage(ProposalNotFound)

      When("I want to update a non existant proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = ProposalId("fake"),
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        moderator = UserId("some user"),
        newContent = None,
        labels = Seq.empty,
        tags = Seq.empty,
        idea = Some(IdeaId("some-idea")),
        question = questionOnNothingFr,
        replyTo = probe.ref
      )

      Then("I should receive 'None' since nothing is found")
      probe.expectMessage(ProposalNotFound)
    }

    Scenario("Update a validated Proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I accept the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq.empty,
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("some-idea")),
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      And("I update this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        moderator = UserId("some user"),
        newContent = Some("This content must be changed"),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("idea-id")),
        question = questionOnTheme,
        replyTo = probe.ref
      )

      Then("I should receive the updated proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("updateCommand"))
      response.content should be("This content must be changed")
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.idea should be(Some(IdeaId("idea-id")))
    }

    Scenario("Update a validated Proposal with no tags") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I accept the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq.empty,
        tags = Seq(),
        idea = Some(IdeaId("some-idea")),
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      And("I update this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        moderator = UserId("some user"),
        newContent = Some("This content must be changed"),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("idea-id")),
        question = questionOnTheme,
        replyTo = probe.ref
      )

      Then("I should receive the updated proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("updateCommand"))
      response.content should be("This content must be changed")
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.idea should be(Some(IdeaId("idea-id")))
    }

    Scenario("Update a validated Proposal with no idea") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I accept the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq.empty,
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      And("I update this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        moderator = UserId("some user"),
        newContent = Some("This content must be changed"),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("idea-id")),
        question = questionOnTheme,
        replyTo = probe.ref
      )

      Then("I should receive the updated proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("updateCommand"))
      response.content should be("This content must be changed")
      response.status should be(Accepted)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
      response.tags should be(Seq(TagId("some tag id")))
      response.labels should be(Seq(LabelId("action")))
      response.idea should be(Some(IdeaId("idea-id")))
    }

    Scenario("Update a non validated Proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I update the proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        moderator = UserId("some user"),
        newContent = None,
        labels = Seq.empty,
        tags = Seq.empty,
        idea = Some(IdeaId("some-idea")),
        question = questionOnNothingFr,
        replyTo = probe.ref
      )

      Then("I should receive a ValidationFailedError")
      val error = probe.expectMessageType[InvalidStateError]
      error.message should be("Proposal updateCommand is not accepted and cannot be updated")
    }
  }

  Feature("Lock a proposal") {
    Scenario("try to lock a non-existing proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a fake proposalId")
      val proposalId = ProposalId("this-is-fake-proposal")
      And("moderator")
      val moderatorId = UserId("mod")

      When("I try to lock the fake proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorId,
        moderatorName = Some("Mod"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("The proposal should not be seen as locked")
      probe.expectMessage(ProposalNotFound)
    }

    Scenario("lock an unlocked proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an unlocked proposal")
      val proposalId: ProposalId = ProposalId("unlockedProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      And("a moderator Mod")
      val moderatorMod = UserId("mod")

      When("I lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod,
        moderatorName = Some("Mod"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("I should receive the moderatorId")
      probe.expectMessage(Envelope(moderatorMod))
    }

    Scenario("expand the time a proposal is locked by yourself") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("an unlocked proposal")
      val proposalId: ProposalId = ProposalId("lockedProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      And("a moderator Mod")
      val moderatorMod = UserId("mod")

      When("I lock the proposal")

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod,
        moderatorName = Some("Mod"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )
      probe.expectMessage(Envelope(moderatorMod))

      And("I lock the proposal again after 10 milli sec")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod,
        moderatorName = Some("Mod"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("I should receive the moderatorId")
      probe.expectMessage(Envelope(moderatorMod))
    }

    Scenario("fail to lock a proposal already locked by someone else") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("two moderators Mod1 & Mod2")
      val moderatorMod1 = UserId("mod1")
      val moderatorMod2 = UserId("mod2")

      And("a proposal locked by Mod1")
      val proposalId: ProposalId = ProposalId("lockedFailProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod1,
        moderatorName = Some("Mod1"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(moderatorMod1))

      When("Mod2 tries to lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod2,
        moderatorName = Some("Mod2"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("Mod2 fails to lock the proposal")
      probe.expectMessage(AlreadyLockedBy("Mod1"))
    }

    Scenario("lock a proposal after lock deadline was reached") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("two moderators Mod1 & Mod2")
      val moderatorMod1 = UserId("mod1")
      val moderatorMod2 = UserId("mod2")

      And("a proposal locked by Mod1")
      val proposalId: ProposalId = ProposalId("lockedExpiredProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod1,
        moderatorName = Some("Mod1"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(moderatorMod1))

      When("Mod2 waits enough time")
      Thread.sleep(150)
      And("Mod2 tries to lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod2,
        moderatorName = Some("Mod2"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      Then("Mod2 succeeds to lock the proposal")
      probe.expectMessage(Envelope(moderatorMod2))
    }

    Scenario("lock a proposal and try to vote after") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a moderator FooModerator")
      val moderator = UserId("FooModerator")

      And("a proposal locked by FooModerator")
      val proposalId: ProposalId = ProposalId("proposal-to-lock")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderator,
        moderatorName = Some("FooModerator"),
        requestContext = RequestContext.empty,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(moderator))

      val voteAgree = Vote(
        key = VoteKey.Agree,
        count = 1,
        countVerified = 1,
        countSequence = 0,
        countSegment = 0,
        qualifications = Seq(
          Qualification(QualificationKey.LikeIt, 0, 0, 0, 0),
          Qualification(QualificationKey.Doable, 0, 0, 0, 0),
          Qualification(QualificationKey.PlatitudeAgree, 0, 0, 0, 0)
        )
      )

      coordinator ! VoteProposalCommand(
        proposalId = proposalId,
        Some(UserId("Bar")),
        RequestContext.empty,
        voteKey = VoteKey.Agree,
        vote = None,
        maybeOrganisationId = None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(voteAgree))

      val voteDisagree = Vote(
        key = VoteKey.Disagree,
        count = 1,
        countVerified = 1,
        countSequence = 0,
        countSegment = 0,
        qualifications = Seq(
          Qualification(QualificationKey.NoWay, 0, 0, 0, 0),
          Qualification(QualificationKey.Impossible, 0, 0, 0, 0),
          Qualification(QualificationKey.PlatitudeDisagree, 0, 0, 0, 0)
        )
      )

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! VoteProposalCommand(
        proposalId = proposalId,
        Some(UserId("Baz")),
        RequestContext.empty,
        voteKey = VoteKey.Disagree,
        vote = None,
        maybeOrganisationId = None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(voteDisagree))
    }
  }

  Feature("Patch a proposal") {
    Scenario("patch creation context") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      probe.expectMessageType[Envelope[Proposal]]

      val dateVisitor = DateHelper.now()
      coordinator ! PatchProposalCommand(
        proposalId,
        UserId("1234"),
        PatchProposalRequest(creationContext = Some(
          PatchRequestContext(
            requestId = Some("my-request-id"),
            sessionId = Some(SessionId("session-id")),
            visitorId = Some(VisitorId("visitor-id")),
            visitorCreatedAt = Some(dateVisitor),
            externalId = Some("external-id"),
            country = Some(Country("BE")),
            detectedCountry = Some(Country("FR")),
            language = Some(Language("nl")),
            operation = Some(OperationId("my-operation-id")),
            source = Some("my-source"),
            location = Some("my-location"),
            question = Some("my-question"),
            hostname = Some("my-hostname"),
            ipAddress = Some("1.2.3.x"),
            ipAddressHash = Some("123456789-sha-512-12345890"),
            getParameters = Some(Map("parameter" -> "value")),
            userAgent = Some("my-user-agent"),
            questionId = Some(QuestionId("my-question-id")),
            applicationName = Some(ApplicationName.Backoffice),
            referrer = Some("my-referrer"),
            customData = Some(Map("my-key-1" -> "my-value-1", "my-key-2" -> "my-value-2"))
          )
        )
        ),
        RequestContext.empty,
        replyTo = probe.ref
      )

      val proposal: Proposal = probe.expectMessageType[Envelope[Proposal]].value
      proposal.creationContext should be(
        RequestContext(
          userId = None,
          requestId = "my-request-id",
          sessionId = SessionId("session-id"),
          visitorId = Some(VisitorId("visitor-id")),
          visitorCreatedAt = Some(dateVisitor),
          externalId = "external-id",
          country = Some(Country("BE")),
          detectedCountry = Some(Country("FR")),
          language = Some(Language("nl")),
          operationId = Some(OperationId("my-operation-id")),
          source = Some("my-source"),
          location = Some("my-location"),
          question = Some("my-question"),
          hostname = Some("my-hostname"),
          ipAddress = Some("1.2.3.x"),
          ipAddressHash = Some("123456789-sha-512-12345890"),
          getParameters = Some(Map("parameter" -> "value")),
          userAgent = Some("my-user-agent"),
          questionId = Some(QuestionId("my-question-id")),
          applicationName = Some(ApplicationName.Backoffice),
          referrer = Some("my-referrer"),
          customData = Map("my-key-1" -> "my-value-1", "my-key-2" -> "my-value-2")
        )
      )
    }

    Scenario("patch some proposal information") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! PatchProposalCommand(
        proposalId,
        UserId("1234"),
        PatchProposalRequest(
          slug = Some("some-custom-slug"),
          content = Some("some content different from the slug"),
          author = Some(UserId("the user id")),
          labels = Some(Seq(LabelId("my-label"))),
          status = Some(Refused),
          refusalReason = Some("I don't want"),
          tags = Some(Seq(TagId("my-tag"))),
          questionId = Some(QuestionId("my-question-id")),
          creationContext = None,
          ideaId = Some(IdeaId("my-idea")),
          operation = Some(OperationId("my-operation-id")),
          initialProposal = Some(false)
        ),
        RequestContext.empty,
        replyTo = probe.ref
      )

      val proposal: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      proposal.slug should be("some-custom-slug")
      proposal.content should be("some content different from the slug")
      proposal.author should be(UserId("the user id"))
      proposal.labels should be(Seq(LabelId("my-label")))
      proposal.status should be(Refused)
      proposal.refusalReason should be(Some("I don't want"))
      proposal.tags should be(Seq(TagId("my-tag")))
      proposal.questionId should be(Some(QuestionId("my-question-id")))
      proposal.idea should be(Some(IdeaId("my-idea")))
      proposal.operation should be(Some(OperationId("my-operation-id")))
      proposal.initialProposal should be(false)
    }

    Scenario("patch other proposal information") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! PatchProposalCommand(
        proposalId,
        UserId("1234"),
        PatchProposalRequest(
          creationContext = None,
          slug = Some("some-custom-slug"),
          author = Some(UserId("the user id")),
          status = Some(Postponed)
        ),
        RequestContext.empty,
        replyTo = probe.ref
      )

      val proposal: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      proposal.slug should be("some-custom-slug")
      proposal.content should be("This is a proposal")
      proposal.author should be(UserId("the user id"))
      proposal.status should be(Postponed)
    }
  }

  Feature("anonymize proposals") {
    Scenario("anonymize proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("anonymized-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = Question(
          questionId = QuestionId("some-question"),
          slug = "some-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "my question",
          shortTitle = None,
          operationId = None
        ),
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AnonymizeProposalCommand(proposalId)

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      val proposal: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      proposal.slug should be("delete-requested")
      proposal.content should be("DELETE_REQUESTED")
      proposal.status should be(Refused)
      proposal.refusalReason should be(Some("other"))
    }
  }

  Feature("update verified votes") {
    val proposalId = ProposalId("updateCommand")
    Scenario("Update the verified votes of validated Proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I accept the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq.empty,
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("some-idea")),
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]].value

      And("I update the verified votes for the Proposal")
      val votesVerified = Seq(
        UpdateVoteRequest(
          key = VoteKey.Agree,
          count = Some(12),
          countVerified = Some(11),
          countSequence = Some(10),
          countSegment = Some(9),
          qualifications = Seq(
            UpdateQualificationRequest(
              QualificationKey.LikeIt,
              count = Some(10),
              countVerified = Some(11),
              countSequence = Some(12),
              countSegment = Some(13)
            ),
            UpdateQualificationRequest(QualificationKey.Doable, countVerified = Some(11)),
            UpdateQualificationRequest(QualificationKey.PlatitudeAgree, countVerified = Some(14))
          )
        ),
        UpdateVoteRequest(
          key = VoteKey.Disagree,
          countVerified = Some(24),
          qualifications = Seq(
            UpdateQualificationRequest(QualificationKey.NoWay, countVerified = Some(4)),
            UpdateQualificationRequest(QualificationKey.Impossible, countVerified = Some(5)),
            UpdateQualificationRequest(QualificationKey.PlatitudeDisagree, countVerified = Some(6))
          )
        ),
        UpdateVoteRequest(
          key = VoteKey.Neutral,
          countVerified = Some(36),
          qualifications = Seq(
            UpdateQualificationRequest(QualificationKey.NoOpinion, countVerified = Some(7)),
            UpdateQualificationRequest(QualificationKey.DoNotUnderstand, countVerified = Some(8)),
            UpdateQualificationRequest(QualificationKey.DoNotCare, countVerified = Some(9))
          )
        )
      )

      coordinator ! UpdateProposalVotesCommand(
        moderator = UserId("some user"),
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        votes = votesVerified,
        replyTo = probe.ref
      )

      Then("I should receive the updated proposal")

      val response: Proposal = probe.expectMessageType[Envelope[Proposal]].value

      response.proposalId should be(ProposalId("updateCommand"))
      val voteAgree = response.votes.find(vote => vote.key == VoteKey.Agree)
      voteAgree.map(_.count) should contain(12)
      voteAgree.map(_.countVerified) should contain(11)
      voteAgree.map(_.countSequence) should contain(10)
      voteAgree.map(_.countSegment) should contain(9)

      val likeIt = voteAgree.flatMap(_.qualifications.find(_.key == LikeIt))
      likeIt.map(_.count) should contain(10)
      likeIt.map(_.countVerified) should contain(11)
      likeIt.map(_.countSequence) should contain(12)
      likeIt.map(_.countSegment) should contain(13)

      response.votes.filter(vote => vote.key == VoteKey.Disagree).head.countVerified should be(24)
      response.votes.filter(vote => vote.key == VoteKey.Neutral).head.countVerified should be(36)
    }
  }

  Feature("update verified votes on refused proposal") {
    val proposalId = ProposalId("update-on-refused")
    Scenario("Update the verified votes of refused Proposal") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      When("I accept the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = None,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      And("I update the verified votes for the Proposal")
      val votesVerified = Seq(
        UpdateVoteRequest(
          key = VoteKey.Agree,
          countVerified = Some(12),
          qualifications = Seq(
            UpdateQualificationRequest(QualificationKey.LikeIt, countVerified = Some(1)),
            UpdateQualificationRequest(QualificationKey.Doable, countVerified = Some(2)),
            UpdateQualificationRequest(QualificationKey.PlatitudeAgree, countVerified = Some(3))
          )
        ),
        UpdateVoteRequest(
          key = VoteKey.Disagree,
          countVerified = Some(24),
          qualifications = Seq(
            UpdateQualificationRequest(QualificationKey.NoWay, countVerified = Some(4)),
            UpdateQualificationRequest(QualificationKey.Impossible, countVerified = Some(5)),
            UpdateQualificationRequest(QualificationKey.PlatitudeDisagree, countVerified = Some(6))
          )
        ),
        UpdateVoteRequest(
          key = VoteKey.Neutral,
          countVerified = Some(36),
          qualifications = Seq(
            UpdateQualificationRequest(QualificationKey.NoOpinion, countVerified = Some(7)),
            UpdateQualificationRequest(QualificationKey.DoNotUnderstand, countVerified = Some(8)),
            UpdateQualificationRequest(QualificationKey.DoNotCare, countVerified = Some(9))
          )
        )
      )

      coordinator ! UpdateProposalVotesCommand(
        moderator = UserId("some user"),
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        votes = votesVerified,
        replyTo = probe.ref
      )

      Then("I should receive an error")

      probe.expectMessage(InvalidStateError(s"Proposal ${proposalId.value} is not accepted and cannot be updated"))
    }
  }

  Feature("vote on proposal") {
    val proposalId = ProposalId("proposal-id")
    Scenario("vote on a new proposal with the valid proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Vote]].value
      response.key should be(VoteKey.Agree)
      response.count should be(1)
      response.countVerified should be(1)
    }

    Scenario("vote on a new proposal with the wrong proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Troll,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Vote]].value
      response.key should be(VoteKey.Agree)
      response.count should be(1)
      response.countVerified should be(0)
    }
  }

  Feature("unvote on proposal") {
    val proposalId = ProposalId("proposal-id")
    Scenario("unvote on a new proposal with the valid proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Vote]]

      coordinator ! UnvoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Vote]].value
      response.key should be(VoteKey.Agree)
      response.count should be(0)
      response.countVerified should be(0)
    }

    Scenario("vote on a new proposal with the wrong proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Vote]]

      coordinator ! UnvoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Troll,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Vote]].value
      response.key should be(VoteKey.Agree)
      response.count should be(0)
      response.countVerified should be(0)
    }
  }

  Feature("qualify on proposal") {
    val proposalId = ProposalId("proposal-id")
    Scenario("qualify on a new proposal with the valid proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Vote]]

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Qualification]].value
      response.key should be(QualificationKey.LikeIt)
      response.count should be(1)
      response.countVerified should be(1)
    }

    Scenario("qualify on a new proposal with the wrong proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Troll,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Vote]]

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Troll,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Qualification]].value
      response.key should be(QualificationKey.LikeIt)
      response.count should be(1)
      response.countVerified should be(0)
    }
  }

  Feature("unqualify on proposal") {
    val proposalId = ProposalId("proposal-id")
    Scenario("unqualify on a new proposal with the valid proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Vote]]

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Qualification]]

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(QualificationKey.LikeIt -> Trusted), DateHelper.now(), Trusted)),
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Qualification]].value
      response.key should be(QualificationKey.LikeIt)
      response.count should be(0)
      response.countVerified should be(0)
    }

    Scenario("unqualify on a new proposal with the wrong proposalKey") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Vote]]

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Qualification]]

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(QualificationKey.LikeIt -> Troll), DateHelper.now(), Troll)),
        voteTrust = Troll,
        replyTo = probe.ref
      )

      val response = probe.expectMessageType[Envelope[Qualification]].value
      response.key should be(QualificationKey.LikeIt)
      response.count should be(0)
      response.countVerified should be(1)
    }
  }

  Feature("troll detection on votes") {
    Scenario("vote and unvote as a troll") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_and_unvote_as_a_troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and unvote as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Troll,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Troll)),
        Troll,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)

    }

    Scenario("trusted vote and unvote as a troll") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("trusted_vote_and_unvote_as_a_troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and unvote as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Trusted,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
    }

    Scenario("vote as a troll and trusted unvote") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_as_a_troll_and_trusted_unvote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and unvote as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Troll,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Troll)),
        Troll,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
    }

    Scenario("trusted vote and troll revote") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("trusted_vote_and_troll_revote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "trusted vote and troll revote",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Trusted,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Neutral,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll,
        replyTo = probe.ref
      )
      val revote = probe.expectMessageType[Envelope[Vote]].value

      revote.count should be(1)
      revote.countVerified should be(0)

      coordinator ! GetProposal(proposalId, RequestContext.empty, probe.ref)

      val votedProposal = probe.expectMessageType[Envelope[Proposal]].value
      val proposalAgree = votedProposal.votes.find(_.key == VoteKey.Agree)
      proposalAgree should be(defined)
      proposalAgree.map(_.count) should be(Some(0))
      proposalAgree.map(_.countVerified) should be(Some(0))

      val proposalNeutral = votedProposal.votes.find(_.key == VoteKey.Neutral)
      proposalNeutral should be(defined)
      proposalNeutral.map(_.count) should be(Some(1))
      proposalNeutral.map(_.countVerified) should be(Some(0))

    }
  }

  Feature("troll detection on qualifications") {
    Scenario("qualify and unqualify as a troll") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_and_unqualify_as_a_troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Trusted,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Troll), DateHelper.now(), Trusted)),
        Troll,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
    }

    Scenario("trusted qualify and unqualify as a troll") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("trusted_qualify_and_unqualify_as_a_troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "trusted qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Trusted,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Trusted,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Trusted), DateHelper.now(), Trusted)),
        Troll,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
    }

    Scenario("qualify as a troll and trusted unqualify") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_as_a_troll_and_trusted_unqualify")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify as a troll and truste unqualify",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Trusted,
        replyTo = probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Troll), DateHelper.now(), Trusted)),
        Trusted,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
    }
  }

  Feature("votes in sequence") {
    Scenario("vote and devote in sequence") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_and_devote_in_sequence")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and devote in sequence",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Sequence,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Sequence)),
        Sequence,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    Scenario("vote in sequence, devote out of it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_in_sequence_then_devote_out_of_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote in sequence, devote out of it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Sequence,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Sequence)),
        Troll,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    Scenario("vote out of sequence, devote in it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_out_of_sequence_then_devote_in_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote out of sequence, devote in it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Troll,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(0)
      vote.countSequence should be(0)
      vote.countSegment should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Troll)),
        Sequence,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }
  }

  Feature("votes in segment") {
    Scenario("vote and devote in segment") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_and_devote_in_sequence")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and devote in sequence",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Segment,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(1)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Segment)),
        Segment,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    Scenario("vote in segment, devote out of it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_in_segment_then_devote_out_of_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote in segment, devote out of it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Segment,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(1)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Segment)),
        Troll,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    Scenario("vote out of segment, devote in it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("vote_out_of_segment_then_devote_in_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote out of segment, devote in it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Trusted,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(0)
      vote.countSegment should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Segment,
        replyTo = probe.ref
      )
      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }
  }

  Feature("qualifications in sequence") {
    Scenario("qualify and unqualify in sequence") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_and_unqualify_in_sequence")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Sequence,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(0)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Sequence)),
        Sequence,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)
      qualification.countSequence should be(1)
      qualification.countSegment should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Sequence), DateHelper.now(), Sequence)),
        Sequence,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    Scenario("qualify in sequence and unqualify out of it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_in_sequence_and_unqualify_out_of_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in sequence and unqualify out of it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Sequence,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(0)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Sequence)),
        Sequence,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)
      qualification.countSequence should be(1)
      qualification.countSegment should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Sequence), DateHelper.now(), Sequence)),
        Troll,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    Scenario("qualify in sequence and unvote") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_in_sequence_and_unvote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in sequence and unvote",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Sequence,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(0)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Sequence)),
        Sequence,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)
      qualification.countSequence should be(1)
      qualification.countSegment should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Sequence), DateHelper.now(), Sequence)),
        Troll,
        replyTo = probe.ref
      )

      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)

      unvote.qualifications.foreach { qualification =>
        qualification.count should be(0)
        qualification.countVerified should be(0)
        qualification.countSequence should be(0)
        qualification.countSegment should be(0)
      }
    }

    Scenario("qualify out of sequence and unqualify in it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_out_of_sequence_and_unqualify_in_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify out of sequence and unqualify in it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Sequence,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(0)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Sequence)),
        Troll,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(0)
      qualification.countSequence should be(0)
      qualification.countSegment should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Troll), DateHelper.now(), Sequence)),
        Sequence,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }
  }

  Feature("qualifications in segment") {
    Scenario("qualify and unqualify in segment") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_and_unqualify_in_segment")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Segment,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Segment)),
        Segment,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)
      qualification.countSequence should be(1)
      qualification.countSegment should be(1)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Segment), DateHelper.now(), Segment)),
        Segment,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    Scenario("qualify in segment and unqualify out of it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_in_segment_and_unqualify_out_of_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in segment and unqualify out of it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Segment,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Segment)),
        Segment,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)
      qualification.countSequence should be(1)
      qualification.countSegment should be(1)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Segment), DateHelper.now(), Segment)),
        Troll,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    Scenario("qualify in segment and unvote") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_in_segment_and_unvote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in sequence and unvote",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Segment,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Segment)),
        Segment,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(1)
      qualification.countSequence should be(1)
      qualification.countSegment should be(1)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Segment), DateHelper.now(), Segment)),
        Troll,
        replyTo = probe.ref
      )

      val unvote = probe.expectMessageType[Envelope[Vote]].value

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)

      unvote.qualifications.foreach { qualification =>
        qualification.count should be(0)
        qualification.countVerified should be(0)
        qualification.countSequence should be(0)
        qualification.countSegment should be(0)
      }
    }

    Scenario("qualify out of segment and unqualify in it") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("qualify_out_of_segment_and_unqualify_in_it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify out of segment and unqualify in it",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme,
        replyTo = probe.ref
      )

      probe.expectMessageType[Envelope[Proposal]]

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        Segment,
        probe.ref
      )

      val vote = probe.expectMessageType[Envelope[Vote]].value

      vote.count should be(1)
      vote.countVerified should be(1)
      vote.countSequence should be(1)
      vote.countSegment should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Segment)),
        Troll,
        replyTo = probe.ref
      )

      val qualification = probe.expectMessageType[Envelope[Qualification]].value

      qualification.count should be(1)
      qualification.countVerified should be(0)
      qualification.countSequence should be(0)
      qualification.countSegment should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Troll), DateHelper.now(), Segment)),
        Segment,
        replyTo = probe.ref
      )

      val unqualification = probe.expectMessageType[Envelope[Qualification]].value

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }
  }

  Feature("keywords") {
    Scenario("add keywords") {
      val probe = testKit.createTestProbe[ProposalActorProtocol]()

      val proposalId = ProposalId("keywords")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "keywords",
        question = questionOnNothingFr,
        initialProposal = false,
        replyTo = probe.ref
      )

      probe.expectMessage(Envelope(proposalId))

      implicit val arbKeyword: Arbitrary[ProposalKeyword] = Arbitrary(for {
        key   <- arbitrary[String]
        label <- arbitrary[String]
      } yield ProposalKeyword(ProposalKeywordKey(key), label))

      forAll { keywords: Seq[ProposalKeyword] =>
        coordinator ! SetKeywordsCommand(proposalId, keywords, RequestContext.empty, probe.ref)
        probe.expectMessageType[Envelope[Proposal]].value.keywords should contain theSameElementsAs keywords
      }
    }
  }
}
