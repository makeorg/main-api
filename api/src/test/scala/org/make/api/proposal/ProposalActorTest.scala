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

import akka.actor.{Actor, ActorRef}
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.proposal.ProposalActor.ProposalState
import org.make.api.sessionhistory.{SessionHistoryCoordinatorService, TransactionalSessionHistoryEvent}
import org.make.api.{ShardingActorTest, TestUtils}
import org.make.core.history.HistoryActions._
import org.make.core.idea.IdeaId
import org.make.core.operation.{Operation, OperationId, OperationKind, OperationStatus}
import org.make.core.proposal.ProposalStatus.{Accepted, Pending, Postponed, Refused}
import org.make.core.proposal.QualificationKey.LikeIt
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError, ValidationFailedError}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

class ProposalActorTest extends ShardingActorTest with GivenWhenThen with StrictLogging with MockitoSugar {

  class Controller {
    def handle(message: Any, sender: ActorRef): Unit = {
      sender ! message
    }
  }

  class ControllableActor(controller: Controller) extends Actor {
    override def receive: Receive = {
      case something => controller.handle(something, sender())
    }
  }

  val questionOnNothingFr = Question(
    questionId = QuestionId("my-question"),
    slug = "my-question",
    country = Country("FR"),
    language = Language("fr"),
    question = "some unsolved question",
    operationId = None
  )

  val questionOnTheme = Question(
    questionId = QuestionId("my-question"),
    slug = "my-question",
    country = Country("FR"),
    language = Language("fr"),
    question = "some unsolved question",
    operationId = None
  )

  val questionOnNothingIT = Question(
    questionId = QuestionId("my-italian-question"),
    slug = "my-question",
    country = Country("IT"),
    language = Language("it"),
    question = "some unsolved question",
    operationId = None
  )

  val operation1: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("operation1"),
    slug = "operation-1",
    defaultLanguage = Language("en"),
    allowedSources = Seq("core"),
    operationKind = OperationKind.PublicConsultation,
    events = List.empty,
    questions = Seq.empty,
    createdAt = None,
    updatedAt = None
  )
  val operation2: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("operation2"),
    slug = "operation-2",
    defaultLanguage = Language("en"),
    allowedSources = Seq("core"),
    operationKind = OperationKind.PublicConsultation,
    events = List.empty,
    questions = Seq.empty,
    createdAt = None,
    updatedAt = None
  )

  val CREATED_DATE_SECOND_MINUS: Int = 10
  val THREAD_SLEEP_MICROSECONDS: Int = 100

  val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService = mock[SessionHistoryCoordinatorService]
  Mockito
    .when(
      sessionHistoryCoordinatorService
        .logTransactionalHistory(ArgumentMatchers.any[TransactionalSessionHistoryEvent[_]])
    )
    .thenReturn(Future.successful({}))

  val coordinator: ActorRef =
    system.actorOf(ProposalCoordinator.props(sessionHistoryCoordinatorService), ProposalCoordinator.name)

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
      country = question.country,
      language = question.language,
      questionId = question.questionId,
      operationId = question.operationId,
      events = List(
        ProposalAction(
          date = mainCreatedAt.get,
          user = mainUserId,
          actionType = ProposalProposeAction.name,
          arguments = Map("content" -> content)
        )
      )
    )

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  feature("Propose a proposal") {
    val proposalId: ProposalId = ProposalId("proposeCommand")
    val proposalItalyId: ProposalId = ProposalId("proposeItalyCommand")
    scenario("Initialize the state if it was empty") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      And("a newly proposed Proposal")

      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsg(proposalId)

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(
        Some(newProposal(proposalId = proposalId, content = "This is a proposal", question = questionOnNothingFr))
      )

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(
        Some(newProposal(proposalId = proposalId, content = "This is a proposal", question = questionOnNothingFr))
      )
    }

    scenario("Initialize the state for a proposal from Italy") {
      Given("an empty state")
      coordinator ! GetProposal(proposalItalyId, RequestContext.empty)
      expectMsg(None)

      And("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalItalyId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an italian proposal",
        question = questionOnNothingIT,
        initialProposal = false
      )

      expectMsg(proposalItalyId)

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalItalyId, RequestContext.empty)

      expectMsg(
        Some(
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

      coordinator ! GetProposal(proposalItalyId, RequestContext.empty)

      expectMsg(
        Some(
          newProposal(
            proposalId = proposalItalyId,
            content = "This is an italian proposal",
            question = questionOnNothingIT
          )
        )
      )
    }
  }

  feature("View a proposal") {
    val proposalId: ProposalId = ProposalId("viewCommand")
    scenario("Fail if ProposalId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      When("a asking for a fake ProposalId")
      coordinator ! ViewProposalCommand(ProposalId("fake"), RequestContext.empty)

      Then("returns None")
      expectMsg(None)
    }

    scenario("Return the state if valid") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

      When("a new Proposal is proposed")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsg(proposalId)

      Then("returns the state")
      coordinator ! ViewProposalCommand(proposalId, RequestContext.empty)
      expectMsg(
        Some(newProposal(proposalId = proposalId, content = "This is a proposal", question = questionOnNothingFr))
      )
    }
  }

  feature("accept a proposal") {
    scenario("accepting a non existing proposal") {
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
        idea = Some(IdeaId("my-id"))
      )

      Then("I should receive 'None' since nothing is found")
      expectMsg(None)

    }

    scenario("accept an existing proposal changing the text") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-changing-text"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnTheme,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        idea = Some(IdeaId("my-idea"))
      )

      Then("I should receive the accepted proposal with modified content")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept given proposal"))

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

    scenario("accept an existing proposal without changing text") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        idea = Some(IdeaId("some-idea"))
      )

      Then("I should receive the accepted proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept given proposal"))

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

    scenario("accept an existing proposal without a theme") {
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
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        idea = Some(IdeaId("some-idea"))
      )

      Then("I should receive the accepted proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept given proposal"))

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

    scenario("validating a validated proposal shouldn't do anything") {
      Given("a validated proposal")
      val originalContent = "This is a proposal that will be validated"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-2"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = Some(IdeaId("some-idea")),
        question = questionOnNothingFr
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

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
        question = questionOnNothingFr
      )

      Then("I should receive an error")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("status")
      error.errors.head.message should be(Some("Proposal to-be-moderated-2 is already validated"))

    }

    scenario("validate a proposal and set the idea") {
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
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        question = questionOnTheme
      )
      val validatedProposal: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      Then("I should have an idea present")
      validatedProposal.proposalId should be(proposalId)
      validatedProposal.idea should be(Some(idea))
      validatedProposal.questionId should be(Some(questionOnTheme.questionId))

      When("I search for the proposal")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      val searchedProposal: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to search"))

      Then("I should have idea present")
      searchedProposal.proposalId should be(proposalId)
      searchedProposal.idea should be(Some(idea))
    }
  }

  feature("refuse a proposal") {
    scenario("refusing a non existing proposal") {
      Given("no proposal corresponding to id 'nothing-there'")
      When("I try to refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("nothing-there"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("nothing")
      )

      Then("I should receive 'None' since nothing is found")
      expectMsg(None)
    }

    scenario("refuse an existing proposal with a refuse reason") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be refused with a reason"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      When("I refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("this proposal is bad")
      )

      Then("I should receive the refused proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to refuse given proposal"))

      response.proposalId should be(ProposalId("to-be-moderated"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Refused)
      response.refusalReason should be(Some("this proposal is bad"))
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
    }

    scenario("refuse an existing proposal without a refuse reason") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be refused without reason"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      When("I refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = None
      )

      Then("I should receive the refused proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to refuse given proposal"))

      response.proposalId should be(ProposalId("to-be-moderated"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Refused)
      response.refusalReason should be(None)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
    }

    scenario("refusing a refused proposal shouldn't do anything") {
      Given("a refused proposal")
      val originalContent = "This is a new proposal"
      coordinator ! ProposeCommand(
        ProposalId("to-be-moderated-2"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("my reason")
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      When("I re-refuse the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some other user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("another reason")
      )

      Then("I should receive an error")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("status")
      error.errors.head.message should be(Some("Proposal to-be-moderated-2 is already refused"))
    }
  }

  feature("postpone a proposal") {
    scenario("postpone a non existing proposal") {
      Given("no proposal corresponding to id 'nothing-there'")
      When("I try to postpone the proposal")
      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("nothing-there"),
        moderator = UserId("some user"),
        requestContext = RequestContext.empty
      )

      Then("I should receive 'None' since nothing is found")
      expectMsg(None)
    }

    scenario("postpone a pending proposal") {
      Given("a freshly created proposal")
      val originalContent = "This is a proposal that will be postponed"
      coordinator ! ProposeCommand(
        ProposalId("to-be-postponed"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      When("i postpone the proposal")
      coordinator ! PostponeProposalCommand(
        moderator = UserId("moderatorFoo"),
        proposalId = ProposalId("to-be-postponed"),
        requestContext = RequestContext.empty
      )

      Then("I should receive the postponed proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to postpone given proposal"))

      response.proposalId should be(ProposalId("to-be-postponed"))
      response.events.length should be(2)
      response.content should be(originalContent)
      response.status should be(Postponed)
      response.author should be(mainUserId)
      response.createdAt.isDefined should be(true)
      response.updatedAt.isDefined should be(true)
    }

    scenario("postpone a refused proposal shouldn't do nothing") {
      Given("a refused proposal")
      val originalContent = "This is a new proposal to refuse"
      coordinator ! ProposeCommand(
        ProposalId("proposal-to-be-refused"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! RefuseProposalCommand(
        proposalId = ProposalId("proposal-to-be-refused"),
        moderator = UserId("fooUser"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = Some("good reason")
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      When("I try to postpone the proposal")
      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("proposal-to-be-refused"),
        moderator = UserId("moderatorFoo"),
        requestContext = RequestContext.empty
      )

      Then("I should receive an error")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("status")
      error.errors.head.message should be(
        Some("Proposal proposal-to-be-refused is already moderated and cannot be postponed")
      )
    }

    scenario("postpone a postponed proposal shouldn't do nothing") {
      Given("a postponed proposal")
      val originalContent = "This is a new proposal to postpone"
      coordinator ! ProposeCommand(
        ProposalId("proposal-to-be-postponed"),
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("proposal-to-be-postponed"),
        moderator = UserId("fooUser"),
        requestContext = RequestContext.empty
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      When("I try to re-postpone the proposal")
      coordinator ! PostponeProposalCommand(
        proposalId = ProposalId("proposal-to-be-postponed"),
        moderator = UserId("moderatorFoo"),
        requestContext = RequestContext.empty
      )

      Then("I should receive an error")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("status")
      error.errors.head.message should be(Some("Proposal proposal-to-be-postponed is already postponed"))
    }
  }

  feature("Update a proposal") {
    val proposalId: ProposalId = ProposalId("updateCommand")
    scenario("Fail if ProposalId doesn't exists") {
      Given("an empty state")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      expectMsg(None)

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
        question = questionOnNothingFr
      )

      Then("I should receive 'None' since nothing is found")
      expectMsg(None)
    }

    scenario("Update a validated Proposal") {
      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept"))

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
        question = questionOnTheme
      )

      Then("I should receive the updated proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to update given proposal"))

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

    scenario("Update a validated Proposal with no tags") {
      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept"))

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
        question = questionOnTheme
      )

      Then("I should receive the updated proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to update given proposal"))

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

    scenario("Update a validated Proposal with no idea") {
      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept"))

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
        question = questionOnTheme
      )

      Then("I should receive the updated proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to update given proposal"))

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

    scenario("Update a non validated Proposal") {
      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        question = questionOnNothingFr
      )

      Then("I should receive a ValidationFailedError")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("proposalId")
      error.errors.head.message should be(Some("Proposal updateCommand is not accepted and cannot be updated"))
    }
  }

  feature("Lock a proposal") {
    scenario("lock an unlocked proposal") {
      Given("an unlocked proposal")
      val proposalId: ProposalId = ProposalId("unlockedProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      And("a moderator Mod")
      val moderatorMod = UserId("mod")

      When("I lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod,
        moderatorName = Some("Mod"),
        requestContext = RequestContext.empty
      )

      Then("I should receive the moderatorId")
      expectMsg(Right(Some(moderatorMod)))
    }

    scenario("expand the time a proposal is locked by yourself") {
      Given("an unlocked proposal")
      val proposalId: ProposalId = ProposalId("lockedProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      And("a moderator Mod")
      val moderatorMod = UserId("mod")

      When("I lock the proposal")
      And("I lock the proposal again after 10 sec")
      val interval = PatienceConfiguration.Interval(Span(1, Seconds))
      val timeout = PatienceConfiguration.Timeout(Span(12, Seconds))
      Eventually.eventually(timeout, interval) {
        coordinator ! LockProposalCommand(
          proposalId = proposalId,
          moderatorId = moderatorMod,
          moderatorName = Some("Mod"),
          requestContext = RequestContext.empty
        )

        Then("I should receive the moderatorId twice")
        expectMsg(Right(Some(moderatorMod)))
      }
    }

    scenario("fail to lock a proposal already locked by someone else") {
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
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod1,
        moderatorName = Some("Mod1"),
        requestContext = RequestContext.empty
      )

      expectMsg(Right(Some(moderatorMod1)))

      When("Mod2 tries to lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod2,
        moderatorName = Some("Mod2"),
        requestContext = RequestContext.empty
      )

      Then("Mod2 fails to lock the proposal")
      expectMsg(Left(ValidationFailedError(Seq(ValidationError("moderatorName", "already_locked", Some("Mod1"))))))
    }

    scenario("reset lock by moderating the proposal") {
      Given("two moderators Mod1 & Mod2")
      val moderatorMod1 = UserId("mod1")
      val moderatorMod2 = UserId("mod2")

      And("a proposal locked by Mod1")
      val proposalId: ProposalId = ProposalId("lockedModerationProposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is an unlocked proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod1,
        moderatorName = Some("Mod1"),
        requestContext = RequestContext.empty
      )

      expectMsg(Right(Some(moderatorMod1)))

      When("Mod1 moderates the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = proposalId,
        moderator = moderatorMod1,
        requestContext = RequestContext.empty,
        sendNotificationEmail = false,
        refusalReason = Some("nothing")
      )

      expectMsgType[Some[ProposalState]]

      And("Mod2 tries to lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod2,
        moderatorName = Some("Mod2"),
        requestContext = RequestContext.empty
      )

      Then("Mod2 succeeds to lock the proposal")
      expectMsg(Right(Some(moderatorMod2)))
    }

    scenario("lock a proposal after lock expiration date was reached") {
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
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod1,
        moderatorName = Some("Mod1"),
        requestContext = RequestContext.empty
      )

      expectMsg(Right(Some(moderatorMod1)))

      When("Mod2 waits more than 20seconds")
      Thread.sleep(15000)
      And("Mod2 tries to lock the proposal")
      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderatorMod2,
        moderatorName = Some("Mod2"),
        requestContext = RequestContext.empty
      )

      Then("Mod2 succeeds to lock the proposal")
      expectMsg(Right(Some(moderatorMod2)))
    }

    scenario("lock a proposal and try to vote after") {
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
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! LockProposalCommand(
        proposalId = proposalId,
        moderatorId = moderator,
        moderatorName = Some("FooModerator"),
        requestContext = RequestContext.empty
      )

      expectMsg(Right(Some(moderator)))

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
        voteTrust = Trusted
      )

      expectMsg(Right(Some(voteAgree)))

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
        voteTrust = Trusted
      )

      expectMsg(Right(Some(voteDisagree)))
    }
  }

  feature("Patch a proposal") {
    scenario("patch creation context") {
      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsg(proposalId)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsgType[Option[Proposal]]

      coordinator ! PatchProposalCommand(
        proposalId,
        UserId("1234"),
        PatchProposalRequest(
          creationContext = Some(
            PatchRequestContext(
              requestId = Some("my-request-id"),
              sessionId = Some(SessionId("session-id")),
              visitorId = Some(VisitorId("visitor-id")),
              externalId = Some("external-id"),
              country = Some(Country("BE")),
              language = Some(Language("nl")),
              operation = None /*Some("my-operation")*/, // TODO: use Operation
              source = Some("my-source"),
              location = Some("my-location"),
              question = Some("my-question"),
              hostname = Some("my-hostname"),
              ipAddress = Some("1.2.3.4"),
              getParameters = Some(Map("parameter" -> "value")),
              userAgent = Some("my-user-agent")
            )
          )
        ),
        RequestContext.empty
      )

      val proposal: Proposal = expectMsgType[Option[Proposal]].get
      proposal.creationContext should be(
        RequestContext(
          currentTheme = None,
          userId = None,
          requestId = "my-request-id",
          sessionId = SessionId("session-id"),
          visitorId = Some(VisitorId("visitor-id")),
          externalId = "external-id",
          country = Some(Country("BE")),
          detectedCountry = None,
          language = Some(Language("nl")),
          operationId = None,
          source = Some("my-source"),
          location = Some("my-location"),
          question = Some("my-question"),
          hostname = Some("my-hostname"),
          ipAddress = Some("1.2.3.4"),
          getParameters = Some(Map("parameter" -> "value")),
          userAgent = Some("my-user-agent"),
          applicationName = None,
          referrer = None
        )
      )
    }

    scenario("patch some proposal information") {
      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsg(proposalId)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsgType[Option[Proposal]]

      coordinator ! PatchProposalCommand(
        proposalId,
        UserId("1234"),
        PatchProposalRequest(
          creationContext = None,
          slug = Some("some-custom-slug"),
          content = Some("some content different from the slug"),
          author = Some(UserId("the user id")),
          labels = Some(Seq(LabelId("my-label"))),
          status = Some(Refused),
          refusalReason = Some("I don't want"),
          ideaId = Some(IdeaId("my-idea")),
          tags = Some(Seq(TagId("my-tag")))
        ),
        RequestContext.empty
      )

      val proposal: Proposal = expectMsgType[Option[Proposal]].get

      proposal.slug should be("some-custom-slug")
      proposal.content should be("some content different from the slug")
      proposal.author should be(UserId("the user id"))
      proposal.labels should be(Seq(LabelId("my-label")))
      proposal.status should be(Refused)
      proposal.refusalReason should be(Some("I don't want"))
      proposal.idea should be(Some(IdeaId("my-idea")))
      proposal.tags should be(Seq(TagId("my-tag")))
      proposal.country should be(Some(Country("FR")))
      proposal.language should be(Some(Language("fr")))
    }

    scenario("patch other proposal information") {
      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsg(proposalId)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsgType[Option[Proposal]]

      coordinator ! PatchProposalCommand(
        proposalId,
        UserId("1234"),
        PatchProposalRequest(
          creationContext = None,
          slug = Some("some-custom-slug"),
          author = Some(UserId("the user id")),
          status = Some(Postponed),
          country = Some(Country("GB")),
          language = Some(Language("en"))
        ),
        RequestContext.empty
      )

      val proposal: Proposal = expectMsgType[Option[Proposal]].get

      proposal.slug should be("some-custom-slug")
      proposal.content should be("This is a proposal")
      proposal.author should be(UserId("the user id"))
      proposal.status should be(Postponed)
      proposal.country should be(Some(Country("GB")))
      proposal.language should be(Some(Language("en")))
    }
  }

  feature("anonymize proposals") {
    scenario("anonymize proposal") {
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
          country = Country("FR"),
          language = Language("fr"),
          question = "my question",
          operationId = None
        ),
        initialProposal = false
      )

      expectMsg(proposalId)

      coordinator ! AnonymizeProposalCommand(proposalId)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      val proposal: Proposal = expectMsgType[Option[Proposal]].get

      proposal.slug should be("delete-requested")
      proposal.content should be("DELETE_REQUESTED")
      proposal.status should be(Refused)
      proposal.refusalReason should be(Some("other"))
    }
  }

  feature("update verified votes") {
    val proposalId = ProposalId("updateCommand")
    scenario("Update the verified votes of validated Proposal") {
      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

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
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept"))

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
        votes = votesVerified
      )

      Then("I should receive the updated proposal")

      val response: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to update given proposal"))

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

  feature("update verified votes on refused proposal") {
    val proposalId = ProposalId("update-on-refused")
    scenario("Update the verified votes of refused Proposal") {
      Given("a newly proposed Proposal")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      When("I accept the proposal")
      coordinator ! RefuseProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        refusalReason = None
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept"))

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
        votes = votesVerified
      )

      Then("I should receive an error")

      expectMsgType[Exception]

    }

  }

  feature("vote on proposal") {
    val proposalId = ProposalId("proposal-id")
    scenario("vote on a new proposal with the valid proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted
      )

      val response = expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))
      response.isDefined should be(true)
      response.get.key should be(VoteKey.Agree)
      response.get.count should be(1)
      response.get.countVerified should be(1)
    }

    scenario("vote on a new proposal with the wrong proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Troll
      )

      val response = expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))
      response.isDefined should be(true)
      response.get.key should be(VoteKey.Agree)
      response.get.count should be(1)
      response.get.countVerified should be(0)
    }
  }

  feature("unvote on proposal") {
    val proposalId = ProposalId("proposal-id")
    scenario("unvote on a new proposal with the valid proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))

      coordinator ! UnvoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted
      )

      val response = expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))
      response.isDefined should be(true)
      response.get.key should be(VoteKey.Agree)
      response.get.count should be(0)
      response.get.countVerified should be(0)
    }

    scenario("vote on a new proposal with the wrong proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))

      coordinator ! UnvoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Troll
      )

      val response = expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))
      response.isDefined should be(true)
      response.get.key should be(VoteKey.Agree)
      response.get.count should be(0)
      response.get.countVerified should be(0)
    }
  }

  feature("qualify on proposal") {
    val proposalId = ProposalId("proposal-id")
    scenario("qualify on a new proposal with the valid proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted
      )

      val response = expectMsgType[Right[Exception, Option[Qualification]]].getOrElse(fail("unable to qualify"))
      response.isDefined should be(true)
      response.get.key should be(QualificationKey.LikeIt)
      response.get.count should be(1)
      response.get.countVerified should be(1)
    }

    scenario("qualify on a new proposal with the wrong proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Troll
      )

      expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Troll
      )

      val response = expectMsgType[Right[Exception, Option[Qualification]]].getOrElse(fail("unable to qualify"))
      response.isDefined should be(true)
      response.get.key should be(QualificationKey.LikeIt)
      response.get.count should be(1)
      response.get.countVerified should be(0)
    }
  }

  feature("unqualify on proposal") {
    val proposalId = ProposalId("proposal-id")
    scenario("unqualify on a new proposal with the valid proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Qualification]]].getOrElse(fail("unable to qualify"))

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(QualificationKey.LikeIt -> Trusted), DateHelper.now(), Trusted)),
        voteTrust = Trusted
      )

      val response = expectMsgType[Right[Exception, Option[Qualification]]].getOrElse(fail("unable to qualify"))
      response.isDefined should be(true)
      response.get.key should be(QualificationKey.LikeIt)
      response.get.count should be(0)
      response.get.countVerified should be(0)
    }

    scenario("unqualify on a new proposal with the wrong proposalKey") {
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! VoteProposalCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        None,
        None,
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Vote]]].getOrElse(fail("unable to vote"))

      coordinator ! QualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        voteTrust = Trusted
      )

      expectMsgType[Right[Exception, Option[Qualification]]].getOrElse(fail("unable to qualify"))

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        Some(UserId("user-id")),
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(QualificationKey.LikeIt -> Troll), DateHelper.now(), Troll)),
        voteTrust = Troll
      )

      val response = expectMsgType[Right[Exception, Option[Qualification]]].getOrElse(fail("unable to qualify"))
      response.isDefined should be(true)
      response.get.key should be(QualificationKey.LikeIt)
      response.get.count should be(0)
      response.get.countVerified should be(1)
    }
  }

  feature("troll detection on votes") {
    scenario("vote and unvote as a troll") {
      val proposalId = ProposalId("vote and unvote as a troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and unvote as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Troll)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Troll)),
        Troll
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)

    }

    scenario("trusted vote and unvote as a troll") {
      val proposalId = ProposalId("trusted vote and unvote as a troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and unvote as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Trusted)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
    }

    scenario("vote as a troll and trusted unvote") {
      val proposalId = ProposalId("vote as a troll and trusted unvote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and unvote as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Troll)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(0)

      coordinator ! UnvoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Troll)),
        Troll
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
    }

    scenario("trusted vote and troll revote") {
      val proposalId = ProposalId("trusted vote and troll revote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "trusted vote and troll revote",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Trusted)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! VoteProposalCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Neutral,
        None,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll
      )
      val revote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to revote"))
        .getOrElse(fail("unable to revote"))

      revote.count should be(1)
      revote.countVerified should be(0)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      val maybeVotedProposal = expectMsgType[Option[Proposal]]
      maybeVotedProposal should be(defined)
      val votedProposal = maybeVotedProposal.get
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

  feature("troll detection on qualifications") {
    scenario("qualify and unqualify as a troll") {
      val proposalId = ProposalId("qualify and unqualify as a troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Trusted)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

      qualification.count should be(1)
      qualification.countVerified should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Troll), DateHelper.now(), Trusted)),
        Troll
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
    }

    scenario("trusted qualify and unqualify as a troll") {
      val proposalId = ProposalId("trusted qualify and unqualify as a troll")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "trusted qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Trusted)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Trusted
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

      qualification.count should be(1)
      qualification.countVerified should be(1)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Trusted), DateHelper.now(), Trusted)),
        Troll
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
    }

    scenario("qualify as a troll and trusted unqualify") {
      val proposalId = ProposalId("qualify as a troll and trusted unqualify")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify as a troll and truste unqualify",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Trusted)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

      vote.count should be(1)
      vote.countVerified should be(1)

      coordinator ! QualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)),
        Troll
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

      qualification.count should be(1)
      qualification.countVerified should be(0)

      coordinator ! UnqualifyVoteCommand(
        proposalId,
        None,
        RequestContext.empty,
        VoteKey.Agree,
        QualificationKey.LikeIt,
        Some(VoteAndQualifications(VoteKey.Agree, Map(LikeIt -> Troll), DateHelper.now(), Trusted)),
        Trusted
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
    }
  }

  feature("votes in sequence") {
    scenario("vote and devote in sequence") {
      val proposalId = ProposalId("vote and devote in sequence")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and devote in sequence",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Sequence)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Sequence
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    scenario("vote in sequence, devote out of it") {
      val proposalId = ProposalId("vote in sequence, devote out of it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote in sequence, devote out of it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Sequence)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Troll
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    scenario("vote out of sequence, devote in it") {
      val proposalId = ProposalId("vote out of sequence, devote in it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote out of sequence, devote in it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Troll)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Sequence
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }
  }

  feature("votes in segment") {
    scenario("vote and devote in segment") {
      val proposalId = ProposalId("vote and devote in sequence")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote and devote in sequence",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Segment)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Segment
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    scenario("vote in segment, devote out of it") {
      val proposalId = ProposalId("vote in segment, devote out of it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote in segment, devote out of it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Segment)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Troll
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }

    scenario("vote out of segment, devote in it") {
      val proposalId = ProposalId("vote out of segment, devote in it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "vote out of segment, devote in it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Trusted)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Segment
      )
      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

      unvote.count should be(0)
      unvote.countVerified should be(0)
      unvote.countSequence should be(0)
      unvote.countSegment should be(0)
    }
  }

  feature("qualifications in sequence") {
    scenario("qualify and unqualify in sequence") {
      val proposalId = ProposalId("qualify and unqualify in sequence")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Sequence)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Sequence
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Sequence
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    scenario("qualify in sequence and unqualify out of it") {
      val proposalId = ProposalId("qualify in sequence and unqualify out of it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in sequence and unqualify out of it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Sequence)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Sequence
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Troll
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    scenario("qualify in sequence and unvote") {
      val proposalId = ProposalId("qualify in sequence and unvote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in sequence and unvote",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Sequence)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Sequence
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Troll
      )

      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

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

    scenario("qualify out of sequence and unqualify in it") {
      val proposalId = ProposalId("qualify out of sequence and unqualify in it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify out of sequence and unqualify in it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Sequence)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Troll
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Sequence
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }
  }

  feature("qualifications in segment") {
    scenario("qualify and unqualify in segment") {
      val proposalId = ProposalId("qualify and unqualify in segment")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify and unqualify as a troll",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Segment)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Segment
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Segment
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    scenario("qualify in segment and unqualify out of it") {
      val proposalId = ProposalId("qualify in segment and unqualify out of it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in segment and unqualify out of it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Segment)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Segment
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Troll
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }

    scenario("qualify in segment and unvote") {
      val proposalId = ProposalId("qualify in segment and unvote")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify in sequence and unvote",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Segment)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Segment
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Troll
      )

      val unvote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to unvote"))
        .getOrElse(fail("unable to unvote"))

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

    scenario("qualify out of segment and unqualify in it") {
      val proposalId = ProposalId("qualify out of segment and unqualify in it")

      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = "qualify out of segment and unqualify in it",
        question = questionOnNothingFr,
        initialProposal = false
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      coordinator ! AcceptProposalCommand(
        proposalId = proposalId,
        moderator = UserId("some user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = None,
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        idea = None,
        question = questionOnTheme
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      coordinator ! VoteProposalCommand(proposalId, None, RequestContext.empty, VoteKey.Agree, None, None, Segment)

      val vote = expectMsgType[Either[Exception, Option[Vote]]]
        .getOrElse(fail("unable to vote"))
        .getOrElse(fail("unable to vote"))

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
        Troll
      )

      val qualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to qualify"))
        .getOrElse(fail("unable to qualify"))

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
        Segment
      )

      val unqualification = expectMsgType[Either[Exception, Option[Qualification]]]
        .getOrElse(fail("unable to unqualify"))
        .getOrElse(fail("unable to unqualify"))

      unqualification.count should be(0)
      unqualification.countVerified should be(0)
      unqualification.countSequence should be(0)
      unqualification.countSegment should be(0)
    }
  }
}
