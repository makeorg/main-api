package org.make.api.proposal

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestKit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ShardingActorTest
import org.make.api.proposal.ProposalActor.ProposalState
import org.make.core.idea.IdeaId
import org.make.core.operation.{Operation, OperationId, OperationStatus}
import org.make.core.proposal.ProposalStatus.{Accepted, Postponed, Refused}
import org.make.core.proposal._
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.session.SessionId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError, ValidationFailedError}
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}

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

  val userHistoryController: Controller = new Controller
  val sessionHistoryController: Controller = new Controller

  val userHistoryActor: ActorRef = system.actorOf(Props(new ControllableActor(userHistoryController)), "user-history")
  val sessionHistoryActor: ActorRef =
    system.actorOf(Props(new ControllableActor(sessionHistoryController)), "session-history")

  val operation1: Operation = Operation(
    OperationStatus.Active,
    OperationId("operation1"),
    "operation-1",
    Seq.empty,
    "en",
    List.empty,
    None,
    None,
    Seq.empty
  )
  val operation2: Operation = Operation(
    OperationStatus.Active,
    OperationId("operation2"),
    "operation-2",
    Seq.empty,
    "en",
    List.empty,
    None,
    None,
    Seq.empty
  )

  val CREATED_DATE_SECOND_MINUS: Int = 10
  val THREAD_SLEEP_MICROSECONDS: Int = 100

  val coordinator: ActorRef =
    system.actorOf(
      ProposalCoordinator.props(userHistoryActor = userHistoryActor, sessionHistoryActor = sessionHistoryActor),
      ProposalCoordinator.name
    )

  val mainUserId: UserId = UserId("1234")
  val mainCreatedAt: Option[ZonedDateTime] = Some(DateHelper.now().minusSeconds(CREATED_DATE_SECOND_MINUS))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(DateHelper.now())

  val user: User = User(
    userId = mainUserId,
    email = "john.snow@the-night-watch.com",
    firstName = None,
    lastName = None,
    lastIp = None,
    hashedPassword = None,
    verified = true,
    enabled = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    resetToken = None,
    verificationTokenExpiresAt = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleCitizen),
    country = "FR",
    language = "fr",
    profile = None
  )

  private def proposal(proposalId: ProposalId, content: String, slug: String, country: Option[String], language: Option[String]) = Proposal(
    proposalId = proposalId,
    author = mainUserId,
    content = content,
    createdAt = mainCreatedAt,
    updatedAt = None,
    slug = slug,
    creationContext = RequestContext.empty,
    labels = Seq(),
    theme = None,
    status = ProposalStatus.Pending,
    tags = Seq(),
    votes = Seq(
      Vote(
        key = VoteKey.Agree,
        qualifications = Seq(
          Qualification(key = QualificationKey.LikeIt),
          Qualification(key = QualificationKey.Doable),
          Qualification(key = QualificationKey.PlatitudeAgree)
        )
      ),
      Vote(
        key = VoteKey.Disagree,
        qualifications = Seq(
          Qualification(key = QualificationKey.NoWay),
          Qualification(key = QualificationKey.Impossible),
          Qualification(key = QualificationKey.PlatitudeDisagree)
        )
      ),
      Vote(
        key = VoteKey.Neutral,
        qualifications = Seq(
          Qualification(key = QualificationKey.DoNotUnderstand),
          Qualification(key = QualificationKey.NoOpinion),
          Qualification(key = QualificationKey.DoNotCare)
        )
      )
    ),
    events = List(
      ProposalAction(
        date = mainCreatedAt.get,
        user = mainUserId,
        actionType = ProposalProposeAction.name,
        arguments = Map("content" -> content)
      )
    ),
    country = country,
    language = language
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
        country = Some("FR"),
        language = Some("fr")
      )

      expectMsg(proposalId)

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(Some(proposal(proposalId = proposalId, content = "This is a proposal", slug = "this-is-a-proposal", country = Some("FR"), language = Some("fr"))))

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetProposal(proposalId, RequestContext.empty)

      expectMsg(Some(proposal(proposalId = proposalId, content = "This is a proposal", slug = "this-is-a-proposal", country = Some("FR"), language = Some("fr"))))
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
        country = Some("IT"),
        language = Some("it")
      )

      expectMsg(proposalItalyId)

      Then("have the proposal state after proposal")

      coordinator ! GetProposal(proposalItalyId, RequestContext.empty)

      expectMsg(Some(proposal(proposalId = proposalItalyId, content = "This is an italian proposal", slug = "this-is-an-italian-proposal", country = Some("IT"), language = Some("it"))))

      And("recover its state after having been kill")
      coordinator ! KillProposalShard(proposalItalyId, RequestContext.empty)

      Thread.sleep(THREAD_SLEEP_MICROSECONDS)

      coordinator ! GetProposal(proposalItalyId, RequestContext.empty)

      expectMsg(Some(proposal(proposalId = proposalItalyId, content = "This is an italian proposal", slug = "this-is-an-italian-proposal", country = Some("IT"), language = Some("it"))))
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
        country = Some("FR"),
        language = Some("fr")
      )

      expectMsg(proposalId)

      Then("returns the state")
      coordinator ! ViewProposalCommand(proposalId, RequestContext.empty)
      expectMsg(Some(proposal(proposalId = proposalId, content = "This is a proposal", slug = "this-is-a-proposal", country = Some("FR"), language = Some("fr"))))
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
        theme = Some(ThemeId("my theme")),
        labels = Seq(),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq(),
        idea = None,
        operation = None
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
        country = Some("FR"),
        language = Some("fr")
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
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq(),
        idea = None,
        operation = None
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
      response.theme should be(Some(ThemeId("my theme")))

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
        country = Some("FR"),
        language = Some("fr")
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
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq(),
        idea = None,
        operation = None
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
      response.theme should be(Some(ThemeId("my theme")))

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
        country = Some("FR"),
        language = Some("fr")
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
        theme = None,
        labels = Seq.empty,
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq.empty,
        idea = None,
        operation = None
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
      response.theme should be(None)
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
        country = Some("FR"),
        language = Some("fr")
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
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq(),
        idea = None,
        operation = None
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      When("I re-validate the proposal")
      coordinator ! AcceptProposalCommand(
        proposalId = ProposalId("to-be-moderated-2"),
        moderator = UserId("some other user"),
        requestContext = RequestContext.empty,
        sendNotificationEmail = true,
        newContent = Some("something different"),
        theme = Some(ThemeId("my theme 2")),
        labels = Seq(LabelId("action2")),
        tags = Seq(TagId("some tag id 2")),
        similarProposals = Seq(),
        idea = None,
        operation = None
      )

      Then("I should receive an error")
      val error = expectMsgType[ValidationFailedError]
      error.errors.head.field should be("status")
      error.errors.head.message should be(Some("Proposal to-be-moderated-2 is already validated"))

    }

    scenario("validate a proposal and set similar proposals") {
      Given("a validated proposal")
      val proposalId = ProposalId("to-be-moderated-with-similar-1")
      val similarProposals = Seq(ProposalId("similar-1"), ProposalId("similar-2"))
      val originalContent = "This is a proposal that will be validated with similar duplicates"
      coordinator ! ProposeCommand(
        proposalId,
        requestContext = RequestContext.empty,
        user = user,
        createdAt = DateHelper.now(),
        content = originalContent,
        country = Some("FR"),
        language = Some("fr")
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
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = similarProposals,
        idea = None,
        operation = None
      )
      val validatedProposal: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      Then("I should have similar proposals present")
      validatedProposal.proposalId should be(proposalId)
      validatedProposal.similarProposals should be(similarProposals)

      When("I search for the proposal")
      coordinator ! GetProposal(proposalId, RequestContext.empty)
      val searchedProposal: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to search"))

      Then("I should have similar proposals present")
      searchedProposal.proposalId should be(proposalId)
      searchedProposal.similarProposals should be(similarProposals)
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
        country = Some("FR"),
        language = Some("fr")
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
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq.empty,
        idea = Some(idea),
        operation = None
      )
      val validatedProposal: Proposal = expectMsgType[Option[Proposal]].getOrElse(fail("unable to propose"))

      Then("I should have an idea present")
      validatedProposal.proposalId should be(proposalId)
      validatedProposal.idea should be(Some(idea))

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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        theme = None,
        labels = Seq.empty,
        tags = Seq.empty,
        similarProposals = Seq(),
        idea = None,
        operation = None
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
        country = Some("FR"),
        language = Some("fr")
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
        theme = Some(ThemeId("my theme")),
        labels = Seq.empty,
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq.empty,
        idea = None,
        operation = None
      )

      expectMsgType[Option[Proposal]].getOrElse(fail("unable to accept"))

      And("I update this Proposal")
      coordinator ! UpdateProposalCommand(
        proposalId = proposalId,
        requestContext = RequestContext.empty,
        updatedAt = mainUpdatedAt.get,
        moderator = UserId("some user"),
        newContent = Some("This content must be changed"),
        theme = Some(ThemeId("my theme")),
        labels = Seq(LabelId("action")),
        tags = Seq(TagId("some tag id")),
        similarProposals = Seq.empty,
        idea = Some(IdeaId("idea-id")),
        operation = None
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
      response.theme should be(Some(ThemeId("my theme")))
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
        country = Some("FR"),
        language = Some("fr")
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
        theme = None,
        labels = Seq.empty,
        tags = Seq.empty,
        similarProposals = Seq.empty,
        idea = None,
        operation = None
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
      )

      expectMsgPF[Unit]() {
        case None => fail("Proposal was not correctly proposed")
        case _    => // ok
      }

      And("a moderator Mod")
      val moderatorMod = UserId("mod")

      When("I lock the proposal")
      And("I lock the proposal again after 10 sec")
      val timeout = Eventually.timeout(Span(11, Seconds))
      Eventually.eventually(timeout) {
        coordinator ! LockProposalCommand(
          proposalId = proposalId,
          moderatorId = moderatorMod,
          moderatorName = Some("Mod"),
          requestContext = RequestContext.empty
        )

        Then("I should receive the moderatorId twice")
        expectMsg(Right(Some(moderatorMod)))

        Thread.sleep(10000)
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
        country = Some("FR"),
        language = Some("fr")
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
      expectMsg(Left(ValidationFailedError(Seq(ValidationError("moderatorName", Some("Mod1"))))))
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        country = Some("FR"),
        language = Some("fr")
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
        qualifications = Seq(
          Qualification(key = QualificationKey.LikeIt),
          Qualification(key = QualificationKey.Doable),
          Qualification(key = QualificationKey.PlatitudeAgree)
        )
      )

      coordinator ! VoteProposalCommand(
        proposalId = proposalId,
        Some(UserId("Bar")),
        RequestContext.empty,
        voteKey = VoteKey.Agree,
        vote = None
      )

      expectMsg(Right(Some(voteAgree)))

      val voteDisagree = Vote(
        key = VoteKey.Disagree,
        count = 1,
        qualifications = Seq(
          Qualification(key = QualificationKey.NoWay),
          Qualification(key = QualificationKey.Impossible),
          Qualification(key = QualificationKey.PlatitudeDisagree)
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
        vote = None
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
        country = Some("FR"),
        language = Some("fr")
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
              currentTheme = Some(ThemeId("my-theme")),
              requestId = Some("my-request-id"),
              sessionId = Some(SessionId("session-id")),
              externalId = Some("external-id"),
              country = Some("BE"),
              language = Some("nl"),
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
          currentTheme = Some(ThemeId("my-theme")),
          requestId = "my-request-id",
          sessionId = SessionId("session-id"),
          externalId = "external-id",
          country = Some("BE"),
          language = Some("nl"),
          operationId = None,
          source = Some("my-source"),
          location = Some("my-location"),
          question = Some("my-question"),
          hostname = Some("my-hostname"),
          ipAddress = Some("1.2.3.4"),
          getParameters = Some(Map("parameter" -> "value")),
          userAgent = Some("my-user-agent")
        )
      )
    }

    scenario("patch proposal information") {
      val proposalId = ProposalId("patched-context")
      coordinator ! ProposeCommand(
        proposalId = proposalId,
        RequestContext.empty,
        user = user,
        createdAt = mainCreatedAt.get,
        content = "This is a proposal",
        country = Some("FR"),
        language = Some("fr")
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
          theme = Some(ThemeId("my-theme")),
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
      proposal.theme should be(Some(ThemeId("my-theme")))
      proposal.status should be(Refused)
      proposal.refusalReason should be(Some("I don't want"))
      proposal.idea should be(Some(IdeaId("my-idea")))
      proposal.tags should be(Seq(TagId("my-tag")))
    }
  }
}
