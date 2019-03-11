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

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.searches.sort.SortOrder
import org.make.api.idea._
import org.make.api.question.{AuthorRequest, QuestionService, QuestionServiceComponent}
import org.make.api.semantic.{PredictedTagsEvent, SemanticComponent, SemanticService, TagsWithModelResponse}
import org.make.api.sessionhistory.{
  ConcurrentModification,
  RequestSessionVoteValues,
  SessionHistoryCoordinatorService,
  SessionHistoryCoordinatorServiceComponent
}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.tagtype.{TagTypeService, TagTypeServiceComponent}
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical._
import org.make.api.technical.security.{SecurityConfiguration, SecurityConfigurationComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.QualificationKey.LikeIt
import org.make.core.proposal.VoteKey.Agree
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.session.SessionId
import org.make.core.tag._
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationFailedError}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito.{never, times, verify}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultProposalServiceComponentTest
    extends MakeUnitTest
    with DefaultProposalServiceComponent
    with TagServiceComponent
    with IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with SemanticComponent
    with EventBusServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent
    with UserServiceComponent
    with QuestionServiceComponent
    with IdeaServiceComponent
    with IdeaMappingServiceComponent
    with TagTypeServiceComponent
    with SecurityConfigurationComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    mock[SessionHistoryCoordinatorService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val semanticService: SemanticService = mock[SemanticService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val proposalJournal: MakeReadJournal = mock[MakeReadJournal]
  override val userJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournal]
  override val actorSystem: ActorSystem = ActorSystem()
  override val userService: UserService = mock[UserService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val tagService: TagService = mock[TagService]
  override val ideaMappingService: IdeaMappingService = mock[IdeaMappingService]
  override val tagTypeService: TagTypeService = mock[TagTypeService]
  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]

  Mockito
    .when(userService.getUsersByUserIds(Seq.empty))
    .thenReturn(Future.successful(Seq.empty))

  Mockito
    .when(tagTypeService.findAll())
    .thenReturn(
      Future.successful(
        Seq(
          TagType(TagTypeId("stake"), "stake", TagTypeDisplay.Displayed),
          TagType(TagTypeId("solution-type"), "Solution Type", TagTypeDisplay.Displayed),
          TagType(TagTypeId("other"), "Other", TagTypeDisplay.Displayed),
        )
      )
    )

  Mockito
    .when(tagService.findByTagIds(Seq.empty))
    .thenReturn(Future.successful(Seq.empty))

  val moderatorId = UserId("moderator-id")

  private val moderator = User(
    userId = moderatorId,
    email = "moderator@make.org",
    firstName = Some("mode"),
    lastName = Some("rator"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleCitizen, Role.RoleModerator),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    availableQuestions = Seq.empty
  )

  Mockito
    .when(userService.getUser(moderatorId))
    .thenReturn(Future.successful(Some(moderator)))

  def user(id: UserId): User = {
    val idString = id.value
    User(
      userId = id,
      email = s"$idString@make.org",
      firstName = Some(idString),
      lastName = None,
      lastIp = None,
      hashedPassword = None,
      enabled = true,
      emailVerified = true,
      lastConnection = DateHelper.now(),
      verificationToken = None,
      verificationTokenExpiresAt = None,
      resetToken = None,
      resetTokenExpiresAt = None,
      roles = Seq(Role.RoleCitizen),
      country = Country("FR"),
      language = Language("fr"),
      profile = None,
      createdAt = None,
      updatedAt = None,
      availableQuestions = Seq.empty
    )
  }

  def indexedProposal(id: ProposalId): IndexedProposal = {
    IndexedProposal(
      id = id,
      userId = UserId(s"user-${id.value}"),
      content = s"proposal with id ${id.value}",
      slug = s"proposal-with-id-${id.value}",
      status = ProposalStatus.Pending,
      createdAt = DateHelper.now(),
      updatedAt = None,
      votes = Seq.empty,
      votesCount = 0,
      toEnrich = false,
      scores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = Author(
        firstName = Some(id.value),
        organisationName = None,
        organisationSlug = None,
        postalCode = None,
        age = None,
        avatarUrl = None
      ),
      organisations = Seq.empty,
      country = Country("FR"),
      language = Language("fr"),
      themeId = None,
      tags = Seq.empty,
      ideaId = None,
      operationId = None,
      questionId = None,
      sequencePool = SequencePool.New,
      initialProposal = false,
      refusalReason = None
    )
  }

  def proposal(id: ProposalId): Proposal = {
    Proposal(
      proposalId = id,
      slug = s"proposal-with-id-${id.value}",
      content = s"proposal with id ${id.value}",
      author = UserId(s"user-${id.value}"),
      status = ProposalStatus.Pending,
      createdAt = Some(DateHelper.now()),
      updatedAt = None,
      votes = Seq.empty,
      labels = Seq.empty,
      country = Some(Country("FR")),
      language = Some(Language("fr")),
      theme = None,
      tags = Seq.empty,
      questionId = None,
      events = List.empty,
      creationContext = RequestContext.empty
    )
  }

  Mockito
    .when(sessionHistoryCoordinatorService.unlockSessionForVote(any[SessionId], any[ProposalId]))
    .thenReturn(Future.successful {})

  Mockito
    .when(
      sessionHistoryCoordinatorService
        .unlockSessionForQualification(any[SessionId], any[ProposalId], any[QualificationKey])
    )
    .thenReturn(Future.successful {})

  feature("next proposal to moderate") {

    def searchQuery(question: String): SearchQuery = SearchQuery(
      filters = Some(
        SearchFilters(
          question = Some(QuestionSearchFilter(Seq(QuestionId(question)))),
          status = Some(StatusSearchFilter(Seq(ProposalStatus.Pending)))
        )
      ),
      sort = Some(Sort(Some(ProposalElasticsearchFieldNames.createdAt), Some(SortOrder.ASC))),
      limit = Some(50)
    )

    scenario("no proposal matching criteria") {

      Mockito
        .when(elasticsearchProposalAPI.searchProposals(matches(searchQuery("vff"))))
        .thenReturn(Future.successful(ProposalsSearchResult(total = 0, results = Seq.empty)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId("vff"),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(None)
      }

    }
    scenario("no proposal can be locked") {

      val defaultProposal =
        Proposal(
          proposalId = ProposalId("unlockable-1"),
          slug = "unlockable-1",
          content = "unlockable-1",
          author = UserId("user-unlockable-1"),
          labels = Seq.empty,
          theme = None,
          status = ProposalStatus.Pending,
          refusalReason = None,
          tags = Seq.empty,
          votes = Seq.empty,
          language = Some(Language("fr")),
          country = Some(Country("FR")),
          creationContext = RequestContext.empty,
          idea = None,
          operation = Some(OperationId("unlockable")),
          createdAt = None,
          updatedAt = None,
          events = Nil
        )

      Mockito
        .when(elasticsearchProposalAPI.searchProposals(matches(searchQuery("no-proposal-to-lock"))))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 2,
                results = Seq(
                  indexedProposal(ProposalId("unlockable-1")),
                  indexedProposal(ProposalId("unlockable-2")),
                  indexedProposal(ProposalId("unlockable-3")),
                  indexedProposal(ProposalId("unlockable-4"))
                )
              )
            )
        )

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(
                ProposalId("unlockable-1"),
                moderator.userId,
                moderator.fullName,
                RequestContext.empty
              )
            )
          )
        )
        .thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(
                ProposalId("unlockable-2"),
                moderator.userId,
                moderator.fullName,
                RequestContext.empty
              )
            )
          )
        )
        .thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(
                ProposalId("unlockable-3"),
                moderator.userId,
                moderator.fullName,
                RequestContext.empty
              )
            )
          )
        )
        .thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(
                ProposalId("unlockable-4"),
                moderator.userId,
                moderator.fullName,
                RequestContext.empty
              )
            )
          )
        )
        .thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("unlockable-1")))
        .thenReturn(Future.successful(Some(defaultProposal)))

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("unlockable-2")))
        .thenReturn(
          Future.successful(
            Some(
              defaultProposal.copy(
                proposalId = ProposalId("unlockable-2"),
                slug = "unlockable-2",
                content = "unlockable-2",
                author = UserId("user-unlockable-2")
              )
            )
          )
        )

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("unlockable-3")))
        .thenReturn(
          Future.successful(
            Some(
              defaultProposal.copy(
                proposalId = ProposalId("unlockable-3"),
                slug = "unlockable-3",
                content = "unlockable-3",
                author = UserId("user-unlockable-3")
              )
            )
          )
        )

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("unlockable-4")))
        .thenReturn(
          Future.successful(
            Some(
              defaultProposal.copy(
                proposalId = ProposalId("unlockable-4"),
                slug = "unlockable-4",
                content = "unlockable-4",
                author = UserId("user-unlockable-4")
              )
            )
          )
        )

      Mockito
        .when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      Mockito
        .when(userService.getUser(UserId("user-unlockable-1")))
        .thenReturn(Future.successful(Some(user(UserId("user-unlockable-1")))))

      Mockito
        .when(userService.getUser(UserId("user-unlockable-2")))
        .thenReturn(Future.successful(Some(user(UserId("user-unlockable-2")))))

      Mockito
        .when(userService.getUser(UserId("user-unlockable-3")))
        .thenReturn(Future.successful(Some(user(UserId("user-unlockable-3")))))

      Mockito
        .when(userService.getUser(UserId("user-unlockable-4")))
        .thenReturn(Future.successful(Some(user(UserId("user-unlockable-4")))))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          questionId = QuestionId("no-proposal-to-lock"),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(None)
      }

    }

    scenario("second proposal can be locked") {

      Mockito
        .when(elasticsearchProposalAPI.searchProposals(matches(searchQuery("lock-second"))))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 2,
                results = Seq(
                  indexedProposal(ProposalId("unlockable")),
                  indexedProposal(ProposalId("lockable")),
                  indexedProposal(ProposalId("ignored-1")),
                  indexedProposal(ProposalId("ignored-2"))
                )
              )
            )
        )

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(ProposalId("unlockable"), moderator.userId, moderator.fullName, RequestContext.empty)
            )
          )
        )
        .thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(ProposalId("lockable"), moderator.userId, moderator.fullName, RequestContext.empty)
            )
          )
        )
        .thenReturn(Future.successful(Some(moderatorId)))

      Mockito
        .when(proposalCoordinatorService.getProposal(matches(ProposalId("unlockable"))))
        .thenReturn(
          Future.successful(
            Some(
              Proposal(
                proposalId = ProposalId("unlockable"),
                slug = "unlockable",
                content = "unlockable",
                author = UserId("user-unlockable"),
                labels = Seq.empty,
                theme = None,
                status = ProposalStatus.Pending,
                refusalReason = None,
                tags = Seq.empty,
                votes = Seq.empty,
                language = Some(Language("fr")),
                country = Some(Country("FR")),
                creationContext = RequestContext.empty,
                idea = None,
                operation = Some(OperationId("unlockable")),
                createdAt = None,
                updatedAt = None,
                events = Nil
              )
            )
          )
        )

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("lockable")))
        .thenReturn(
          Future.successful(
            Some(
              Proposal(
                proposalId = ProposalId("lockable"),
                slug = "lockable",
                content = "lockable",
                author = UserId("user-lockable"),
                labels = Seq.empty,
                theme = None,
                status = ProposalStatus.Pending,
                refusalReason = None,
                tags = Seq.empty,
                votes = Seq.empty,
                language = Some(Language("fr")),
                country = Some(Country("FR")),
                creationContext = RequestContext.empty,
                idea = None,
                operation = Some(OperationId("lock-second")),
                createdAt = None,
                updatedAt = None,
                events = Nil
              )
            )
          )
        )

      Mockito
        .when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      Mockito
        .when(userService.getUser(UserId("user-unlockable")))
        .thenReturn(Future.successful(Some(user(UserId("user-unlockable")))))

      Mockito
        .when(userService.getUser(UserId("user-lockable")))
        .thenReturn(Future.successful(Some(user(UserId("user-lockable")))))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId("lock-second"),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal.isDefined should be(true)
        maybeProposal.get.proposalId should be(ProposalId("lockable"))
      }

    }

    scenario("first proposal can be locked") {

      Mockito
        .when(elasticsearchProposalAPI.searchProposals(matches(searchQuery("lock-first"))))
        .thenReturn(
          Future
            .successful(ProposalsSearchResult(total = 2, results = Seq(indexedProposal(ProposalId("lockable")))))
        )

      Mockito
        .when(
          proposalCoordinatorService.lock(
            matches(
              LockProposalCommand(ProposalId("lockable"), moderator.userId, moderator.fullName, RequestContext.empty)
            )
          )
        )
        .thenReturn(Future.successful(Some(moderatorId)))

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("lockable")))
        .thenReturn(
          Future.successful(
            Some(
              Proposal(
                proposalId = ProposalId("lockable"),
                slug = "lockable",
                content = "lockable",
                author = UserId("user-lockable"),
                labels = Seq.empty,
                theme = None,
                status = ProposalStatus.Pending,
                refusalReason = None,
                tags = Seq.empty,
                votes = Seq.empty,
                language = Some(Language("fr")),
                country = Some(Country("FR")),
                creationContext = RequestContext.empty,
                idea = None,
                operation = Some(OperationId("lock-second")),
                createdAt = None,
                updatedAt = None,
                events = Nil
              )
            )
          )
        )

      Mockito
        .when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      Mockito
        .when(userService.getUser(UserId("user-lockable")))
        .thenReturn(Future.successful(Some(user(UserId("user-lockable")))))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId("lock-first"),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal.isDefined should be(true)
        maybeProposal.get.proposalId should be(ProposalId("lockable"))
      }

    }
  }

  feature("search proposals voted by user") {

    val paul: User = user(UserId("paul-user-id"))
    scenario("user has no votes on the proposals") {
      Mockito
        .when(
          userHistoryCoordinatorService
            .retrieveVotedProposals(ArgumentMatchers.eq(RequestUserVotedProposals(userId = paul.userId)))
        )
        .thenReturn(Future.successful(Seq.empty))

      Mockito
        .when(userHistoryCoordinatorService.retrieveVoteAndQualifications(ArgumentMatchers.any[RequestVoteValues]))
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      whenReady(
        proposalService.searchProposalsVotedByUser(
          userId = paul.userId,
          filterVotes = None,
          filterQualifications = None,
          requestContext = RequestContext.empty
        ),
        Timeout(3.seconds)
      ) { proposalResultResponse =>
        proposalResultResponse.total should be(0)
        proposalResultResponse.results should be(Seq.empty)
      }
    }

    scenario("user has votes on some proposals") {

      val gilProposal1 = indexedProposal(ProposalId("gil-1"))
      val gilProposal2 = indexedProposal(ProposalId("gil-2"))
      val gil: User = user(UserId("gil-user-id"))
      Mockito
        .when(
          userHistoryCoordinatorService
            .retrieveVotedProposals(ArgumentMatchers.eq(RequestUserVotedProposals(userId = gil.userId)))
        )
        .thenReturn(Future.successful(Seq(gilProposal1.id, gilProposal2.id)))
      Mockito
        .when(userHistoryCoordinatorService.retrieveVoteAndQualifications(ArgumentMatchers.any[RequestVoteValues]))
        .thenReturn(
          Future.successful(
            Map(
              gilProposal2.id -> VoteAndQualifications(
                Agree,
                Seq(LikeIt),
                ZonedDateTime.parse("2018-03-01T16:09:30.441Z")
              ),
              gilProposal1.id -> VoteAndQualifications(
                Agree,
                Seq(LikeIt),
                ZonedDateTime.parse("2018-03-02T16:09:30.441Z")
              )
            )
          )
        )

      Mockito
        .when(elasticsearchProposalAPI.searchProposals(ArgumentMatchers.any[SearchQuery]))
        .thenReturn(Future.successful(ProposalsSearchResult(total = 2, results = Seq(gilProposal2, gilProposal1))))

      whenReady(
        proposalService.searchProposalsVotedByUser(
          userId = gil.userId,
          filterVotes = None,
          filterQualifications = None,
          requestContext = RequestContext.empty
        ),
        Timeout(3.seconds)
      ) { proposalsResultResponse =>
        proposalsResultResponse.total should be(2)
        proposalsResultResponse.results.head.id should be(gilProposal1.id)
        proposalsResultResponse.results.last.id should be(gilProposal2.id)
      }
    }
  }

  feature("createInitialProposal") {
    scenario("createInitialProposal") {

      val question =
        Question(
          QuestionId("createInitialProposal"),
          slug = "create-initial-proposal",
          country = Country("FR"),
          language = Language("fr"),
          question = "how to create initial proposals?",
          operationId = None,
          themeId = None
        )

      Mockito
        .when(userService.retrieveOrCreateVirtualUser(any[AuthorRequest], any[Country], any[Language]))
        .thenReturn(Future.successful(user(UserId("user"))))

      Mockito
        .when(userService.getUser(any[UserId]))
        .thenReturn(Future.successful(Some(user(UserId("user")))))

      Mockito
        .when(proposalCoordinatorService.propose(any[ProposeCommand]))
        .thenReturn(Future.successful(ProposalId("my-proposal")))

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("my-proposal")))
        .thenReturn(Future.successful(Some(proposal(ProposalId("my-proposal")))))

      Mockito
        .when(ideaMappingService.getOrCreateMapping(question.questionId, None, None))
        .thenReturn(
          Future.successful(IdeaMapping(IdeaMappingId("mapping"), question.questionId, None, None, IdeaId("my-idea")))
        )

      Mockito
        .when(tagService.findByTagIds(Seq(TagId("my-tag"))))
        .thenReturn(Future.successful(Seq.empty))

      Mockito
        .when(
          proposalCoordinatorService.accept(
            matches(
              AcceptProposalCommand(
                proposalId = ProposalId("my-proposal"),
                moderator = moderatorId,
                requestContext = RequestContext.empty,
                sendNotificationEmail = false,
                newContent = None,
                question = question,
                labels = Seq.empty,
                tags = Seq(TagId("my-tag")),
                idea = Some(IdeaId("my-idea"))
              )
            )
          )
        )
        .thenReturn(Future.successful(None))

      Mockito
        .when(tagService.findByTagIds(Seq(TagId("my-tag"))))
        .thenReturn(Future.successful(Seq.empty))

      val result = proposalService.createInitialProposal(
        "my content",
        question,
        Seq(TagId("my-tag")),
        moderator = moderatorId,
        moderatorRequestContext = RequestContext.empty,
        author = AuthorRequest(None, None, None, None, None)
      )

      whenReady(result, Timeout(5.seconds)) { proposalId =>
        proposalId.value should be("my-proposal")
      }
    }
  }

  feature("get tags for proposal") {
    def tag(id: String) = Tag(
      tagId = TagId(id),
      label = "label",
      display = TagDisplay.Inherit,
      tagTypeId = TagTypeId("tag-type-id"),
      weight = 1.0F,
      operationId = None,
      questionId = Some(QuestionId("question-id")),
      themeId = None,
      country = Country("FR"),
      language = Language("fr")
    )
    def proposal(questionId: Option[QuestionId], tags: Seq[TagId]) = Proposal(
      proposalId = ProposalId("proposal-id"),
      slug = "proposal",
      content = "proposal",
      author = UserId("author"),
      labels = Seq.empty,
      tags = tags,
      votes = Seq.empty,
      questionId = questionId,
      creationContext = RequestContext.empty,
      createdAt = None,
      updatedAt = None,
      events = List.empty
    )

    scenario("proposal without tags") {
      Mockito
        .when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq(tag("id-1"), tag("id-2"), tag("id-3"))))
      Mockito
        .when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(Future.successful(TagsWithModelResponse(tags = Seq(tag("id-3")), modelName = "auto")))

      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(Some(QuestionId("question-id")), Seq.empty))
      whenReady(result, Timeout(5.seconds)) { tagsWithModel =>
        val tagsForProposal = tagsWithModel.tags
        tagsForProposal.size should be(3)
        tagsForProposal.exists(_.id == TagId("id-1")) shouldBe true
        tagsForProposal.exists(_.id == TagId("id-2")) shouldBe true
        tagsForProposal.exists(_.id == TagId("id-3")) shouldBe true

        val tag1 = tagsForProposal.find(_.id == TagId("id-1")).get
        val tag2 = tagsForProposal.find(_.id == TagId("id-2")).get
        val tag3 = tagsForProposal.find(_.id == TagId("id-3")).get

        tag1.checked shouldBe false
        tag2.checked shouldBe false
        tag3.checked shouldBe true

        tag1.predicted shouldBe false
        tag2.predicted shouldBe false
        tag3.predicted shouldBe true
      }
    }
    scenario("proposal with tags") {
      Mockito
        .when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq(tag("id-1"), tag("id-2"), tag("id-3"))))
      Mockito
        .when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(Future.successful(TagsWithModelResponse(tags = Seq(tag("id-3")), modelName = "auto")))

      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(Some(QuestionId("question-id")), Seq(TagId("id-2"))))
      whenReady(result, Timeout(5.seconds)) { tagsWithModel =>
        val tagsForProposal = tagsWithModel.tags
        tagsForProposal.size should be(3)
        tagsForProposal.exists(_.id == TagId("id-1")) shouldBe true
        tagsForProposal.exists(_.id == TagId("id-2")) shouldBe true
        tagsForProposal.exists(_.id == TagId("id-3")) shouldBe true

        val tag1 = tagsForProposal.find(_.id == TagId("id-1")).get
        val tag2 = tagsForProposal.find(_.id == TagId("id-2")).get
        val tag3 = tagsForProposal.find(_.id == TagId("id-3")).get

        tag1.checked shouldBe false
        tag2.checked shouldBe true
        tag3.checked shouldBe false

        tag1.predicted shouldBe false
        tag2.predicted shouldBe false
        tag3.predicted shouldBe true
      }
    }
    scenario("proposal without question") {
      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(None, Seq.empty))
      whenReady(result, Timeout(5.seconds)) { tagsForProposal =>
        tagsForProposal.tags.isEmpty shouldBe true
      }
    }
    scenario("proposal without tags in question") {
      Mockito
        .when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq.empty))
      Mockito
        .when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(Future.successful(TagsWithModelResponse.empty))

      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(Some(QuestionId("question-id")), Seq(TagId("id-2"))))
      whenReady(result, Timeout(5.seconds)) { tagsForProposal =>
        tagsForProposal.tags.isEmpty shouldBe true
      }
    }
    scenario("semantic fails to return a result") {
      Mockito
        .when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq.empty))
      Mockito
        .when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(Future.failed(new RuntimeException("This is an expected exception.")))

      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(Some(QuestionId("question-id")), Seq(TagId("id-2"))))
      whenReady(result, Timeout(5.seconds)) { tagsForProposal =>
        tagsForProposal.tags.isEmpty shouldBe true
      }
    }
  }

  feature("validate proposal") {
    val proposalId = ProposalId("validate-proposal")

    val question = Question(
      questionId = QuestionId("question-id"),
      slug = "question",
      country = Country("FR"),
      language = Language("fr"),
      question = "question",
      operationId = None,
      themeId = None
    )

    val validatedProposal = proposal(proposalId)

    Mockito
      .when(ideaMappingService.getOrCreateMapping(question.questionId, None, None))
      .thenReturn(
        Future.successful(IdeaMapping(IdeaMappingId("mapping"), question.questionId, None, None, IdeaId("my-idea")))
      )

    Mockito
      .when(proposalCoordinatorService.getProposal(proposalId))
      .thenReturn(Future.successful(Some(validatedProposal)))

    Mockito
      .when(
        proposalCoordinatorService.accept(
          matches(
            AcceptProposalCommand(
              proposalId = proposalId,
              moderator = moderatorId,
              requestContext = RequestContext.empty,
              sendNotificationEmail = false,
              newContent = None,
              question = question,
              labels = Seq.empty,
              tags = Seq.empty,
              idea = None
            )
          )
        )
      )
      .thenReturn(Future.successful(Some(validatedProposal)))

    scenario("validate without predicted tags") {
      whenReady(
        proposalService.validateProposal(
          proposalId = proposalId,
          moderator = moderatorId,
          requestContext = RequestContext.empty,
          question = question,
          newContent = None,
          sendNotificationEmail = false,
          idea = None,
          tags = Seq.empty,
          predictedTags = None,
          predictedTagsModelName = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal.isDefined should be(true)
        maybeProposal.map(_.proposalId) should be(Some(proposalId))
      }
    }

    scenario("validate with predicted tags") {
      whenReady(
        proposalService.validateProposal(
          proposalId = proposalId,
          moderator = moderatorId,
          requestContext = RequestContext.empty,
          question = question,
          newContent = None,
          sendNotificationEmail = false,
          idea = None,
          tags = Seq.empty,
          predictedTags = Some(Seq(TagId("predicted-tag-id"))),
          predictedTagsModelName = Some("auto")
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        verify(eventBusService, times(1))
          .publish(matches(PredictedTagsEvent(proposalId, Seq(TagId("predicted-tag-id")), Seq.empty, "auto")))
        maybeProposal.isDefined should be(true)
        maybeProposal.map(_.proposalId) should be(Some(proposalId))
      }
    }
  }

  feature("update proposal") {
    scenario("update proposal with both tags") {

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("update-proposal")))
        .thenReturn(Future.successful(Some(proposal(ProposalId("update-proposal")))))

      Mockito
        .when(userService.getUser(UserId("user-update-proposal")))
        .thenReturn(Future.successful(Some(user(UserId("user-update-proposal")))))

      val tagIds = Seq(TagId("stake-1"), TagId("stake-2"), TagId("solution-1"), TagId("solution-2"), TagId("other"))

      Mockito
        .when(tagService.findByTagIds(tagIds))
        .thenReturn(
          Future.successful(
            Seq(
              Tag(
                TagId("stake-1"),
                "stake 1",
                TagDisplay.Inherit,
                TagTypeId("stake"),
                50.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              ),
              Tag(
                TagId("stake-2"),
                "stake 2",
                TagDisplay.Inherit,
                TagTypeId("stake"),
                80.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              ),
              Tag(
                TagId("solution-1"),
                "solution 1",
                TagDisplay.Inherit,
                TagTypeId("solution-type"),
                50.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              ),
              Tag(
                TagId("solution-2"),
                "solution type 2",
                TagDisplay.Inherit,
                TagTypeId("solution-type"),
                20.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              ),
              Tag(
                TagId("other"),
                "other",
                TagDisplay.Inherit,
                TagTypeId("other"),
                50.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              )
            )
          )
        )

      Mockito
        .when(
          ideaMappingService
            .getOrCreateMapping(QuestionId("update-proposal"), Some(TagId("stake-2")), Some(TagId("solution-1")))
        )
        .thenReturn(
          Future.successful(
            IdeaMapping(
              IdeaMappingId("result"),
              QuestionId("update-proposal"),
              Some(TagId("stake-2")),
              Some(TagId("solution-1")),
              IdeaId("update-idea")
            )
          )
        )

      val question = Question(
        QuestionId("update-proposal"),
        "update-proposal",
        Country("FR"),
        Language("fr"),
        "how to update a proposal?",
        None,
        None
      )

      Mockito
        .when(
          proposalCoordinatorService.update(
            UpdateProposalCommand(
              moderatorId,
              ProposalId("update-proposal"),
              RequestContext.empty,
              ZonedDateTime.parse("2019-01-16T16:48:00Z"),
              None,
              Seq.empty,
              tagIds,
              Some(IdeaId("update-idea")),
              question
            )
          )
        )
        .thenReturn(
          Future.successful(
            Some(proposal(ProposalId("update-proposal")).copy(tags = tagIds, idea = Some(IdeaId("update-idea"))))
          )
        )

      val update = proposalService.update(
        ProposalId("update-proposal"),
        moderatorId,
        RequestContext.empty,
        ZonedDateTime.parse("2019-01-16T16:48:00Z"),
        newContent = None,
        question = question,
        tags = tagIds,
        idea = None,
        predictedTags = None,
        predictedTagsModelName = None
      )

      whenReady(update, Timeout(5.seconds)) { maybeProposal =>
        maybeProposal.flatMap(_.idea) should contain(IdeaId("update-idea"))
      }

    }

    scenario("update proposal when the moderator provides an idea, use it") {

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("update-proposal-3")))
        .thenReturn(Future.successful(Some(proposal(ProposalId("update-proposal-3")))))

      Mockito
        .when(userService.getUser(UserId("user-update-proposal-3")))
        .thenReturn(Future.successful(Some(user(UserId("user-update-proposal-3")))))

      val tagIds = Seq(TagId("stake-1"), TagId("stake-2"), TagId("solution-1"), TagId("solution-2"), TagId("other"))

      val question = Question(
        QuestionId("update-proposal"),
        "update-proposal",
        Country("FR"),
        Language("fr"),
        "how to update a proposal?",
        None,
        None
      )

      Mockito
        .when(
          proposalCoordinatorService.update(
            UpdateProposalCommand(
              moderatorId,
              ProposalId("update-proposal-3"),
              RequestContext.empty,
              ZonedDateTime.parse("2019-01-16T16:48:00Z"),
              None,
              Seq.empty,
              tagIds,
              Some(IdeaId("moderator-idea")),
              question
            )
          )
        )
        .thenReturn(
          Future.successful(
            Some(proposal(ProposalId("update-proposal-3")).copy(tags = tagIds, idea = Some(IdeaId("moderator-idea"))))
          )
        )

      val update = proposalService.update(
        ProposalId("update-proposal-3"),
        moderatorId,
        RequestContext.empty,
        ZonedDateTime.parse("2019-01-16T16:48:00Z"),
        newContent = None,
        question = question,
        tags = tagIds,
        idea = Some(IdeaId("moderator-idea")),
        predictedTags = None,
        predictedTagsModelName = None
      )

      whenReady(update, Timeout(5.seconds)) { maybeProposal =>
        maybeProposal.flatMap(_.idea) should contain(IdeaId("moderator-idea"))
      }

    }

    scenario("update proposal with no stake tags") {

      Mockito
        .when(proposalCoordinatorService.getProposal(ProposalId("update-proposal-4")))
        .thenReturn(Future.successful(Some(proposal(ProposalId("update-proposal-4")))))

      Mockito
        .when(userService.getUser(UserId("user-update-proposal-4")))
        .thenReturn(Future.successful(Some(user(UserId("user-update-proposal-4")))))

      val tagIds = Seq(TagId("solution-1"), TagId("solution-2"), TagId("other"))

      Mockito
        .when(tagService.findByTagIds(tagIds))
        .thenReturn(
          Future.successful(
            Seq(
              Tag(
                TagId("solution-1"),
                "solution 1",
                TagDisplay.Inherit,
                TagTypeId("solution-type"),
                50.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              ),
              Tag(
                TagId("solution-2"),
                "solution type 2",
                TagDisplay.Inherit,
                TagTypeId("solution-type"),
                20.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              ),
              Tag(
                TagId("other"),
                "other",
                TagDisplay.Inherit,
                TagTypeId("other"),
                50.0F,
                None,
                None,
                None,
                Country("FR"),
                Language("fr")
              )
            )
          )
        )

      Mockito
        .when(
          ideaMappingService
            .getOrCreateMapping(QuestionId("update-proposal"), None, Some(TagId("solution-1")))
        )
        .thenReturn(
          Future.successful(
            IdeaMapping(
              IdeaMappingId("result-2"),
              QuestionId("update-proposal"),
              None,
              Some(TagId("solution-1")),
              IdeaId("update-idea-2")
            )
          )
        )

      val question = Question(
        QuestionId("update-proposal"),
        "update-proposal",
        Country("FR"),
        Language("fr"),
        "how to update a proposal?",
        None,
        None
      )

      Mockito
        .when(
          proposalCoordinatorService.update(
            UpdateProposalCommand(
              moderatorId,
              ProposalId("update-proposal-4"),
              RequestContext.empty,
              ZonedDateTime.parse("2019-01-16T16:48:00Z"),
              None,
              Seq.empty,
              tagIds,
              Some(IdeaId("update-idea-2")),
              question
            )
          )
        )
        .thenReturn(
          Future.successful(
            Some(proposal(ProposalId("update-proposal")).copy(tags = tagIds, idea = Some(IdeaId("update-idea-2"))))
          )
        )

      val update = proposalService.update(
        ProposalId("update-proposal-4"),
        moderatorId,
        RequestContext.empty,
        ZonedDateTime.parse("2019-01-16T16:48:00Z"),
        newContent = None,
        question = question,
        tags = tagIds,
        idea = None,
        predictedTags = None,
        predictedTagsModelName = None
      )

      whenReady(update, Timeout(5.seconds)) { maybeProposal =>
        maybeProposal.flatMap(_.idea) should contain(IdeaId("update-idea-2"))
      }

    }

    val proposalId = ProposalId("update-proposal")

    val question = Question(
      questionId = QuestionId("question-id"),
      slug = "question",
      country = Country("FR"),
      language = Language("fr"),
      question = "question",
      operationId = None,
      themeId = None
    )

    val now = DateHelper.now()

    val updatedProposal = proposal(proposalId)

    Mockito
      .when(proposalCoordinatorService.update(any[UpdateProposalCommand]))
      .thenReturn(Future.successful(Some(updatedProposal)))

    Mockito
      .when(
        proposalCoordinatorService.update(
          matches(
            UpdateProposalCommand(
              proposalId = proposalId,
              moderator = moderatorId,
              requestContext = RequestContext.empty,
              updatedAt = now,
              newContent = None,
              question = question,
              labels = Seq.empty,
              tags = Seq.empty,
              idea = None
            )
          )
        )
      )
      .thenReturn(Future.successful(Some(updatedProposal)))

    scenario("update without predicted tags") {
      whenReady(
        proposalService.update(
          proposalId = proposalId,
          moderator = moderatorId,
          requestContext = RequestContext.empty,
          updatedAt = now,
          newContent = None,
          question = question,
          idea = None,
          tags = Seq.empty,
          predictedTags = None,
          predictedTagsModelName = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal.isDefined should be(true)
        maybeProposal.map(_.proposalId) should be(Some(proposalId))
      }
    }

    scenario("update with predicted tags") {
      whenReady(
        proposalService.update(
          proposalId = proposalId,
          moderator = moderatorId,
          requestContext = RequestContext.empty,
          updatedAt = DateHelper.now(),
          newContent = None,
          question = question,
          idea = None,
          tags = Seq.empty,
          predictedTags = Some(Seq(TagId("predicted-tag-id"))),
          predictedTagsModelName = Some("auto")
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        verify(eventBusService, times(1))
          .publish(matches(PredictedTagsEvent(proposalId, Seq(TagId("predicted-tag-id")), Seq.empty, "auto")))
        maybeProposal.isDefined should be(true)
        maybeProposal.map(_.proposalId) should be(Some(proposalId))
      }
    }
  }

  feature("vote") {
    scenario("vote ok unlogged") {
      val sessionId = SessionId("vote-ok")
      val proposalId = ProposalId("vote-ok")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      Mockito
        .when(
          proposalCoordinatorService
            .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None))
        )
        .thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, Seq.empty))))

      whenReady(proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree), Timeout(5.seconds)) {
        _ =>
          verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    scenario("already voted unlogged") {
      val sessionId = SessionId("already-voted")
      val proposalId = ProposalId("already-voted")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)
      val votes = VoteAndQualifications(VoteKey.Agree, Seq.empty, DateHelper.now())

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})

      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map(proposalId -> votes)))
      Mockito
        .when(
          proposalCoordinatorService
            .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, Some(votes)))
        )
        .thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, Seq.empty))))

      whenReady(proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree), Timeout(5.seconds)) {
        _ =>
          verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }

    }

    scenario("vote failed voted unlogged") {
      val sessionId = SessionId("vote-failed-voted-unlogged")
      val proposalId = ProposalId("vote-failed-voted-unlogged")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      Mockito
        .when(
          proposalCoordinatorService
            .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None))
        )
        .thenReturn(Future.failed(new IllegalArgumentException("You shall not, vote!")))

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    scenario("vote failed on lock not acquired") {
      val sessionId = SessionId("vote-failed-on-lock-not-acquired")
      val proposalId = ProposalId("vote-failed-on-lock-not-acquired")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.failed(ConcurrentModification("talk to my hand")))

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never()).unlockSessionForVote(sessionId, proposalId)
        verify(proposalCoordinatorService, never()).vote(
          VoteProposalCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            maybeOrganisationId = None,
            vote = None
          )
        )
      }
    }
  }

  feature("unvote") {
    scenario("unvote ok unlogged") {
      val sessionId = SessionId("unvote-ok")
      val proposalId = ProposalId("unvote-ok")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      Mockito
        .when(
          proposalCoordinatorService
            .unvote(UnvoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None))
        )
        .thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, Seq.empty))))

      whenReady(proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree), Timeout(5.seconds)) {
        _ =>
          verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    scenario("already unvoted unlogged") {
      val sessionId = SessionId("already-unvoted")
      val proposalId = ProposalId("already-unvoted")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)
      val votes = VoteAndQualifications(VoteKey.Agree, Seq.empty, DateHelper.now())

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})

      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map(proposalId -> votes)))
      Mockito
        .when(
          proposalCoordinatorService
            .unvote(UnvoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, Some(votes)))
        )
        .thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, Seq.empty))))

      whenReady(proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree), Timeout(5.seconds)) {
        _ =>
          verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }

    }

    scenario("unvote failed unvoted unlogged") {
      val sessionId = SessionId("unvote-failed-unvoted-unlogged")
      val proposalId = ProposalId("unvote-failed-unvoted-unlogged")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      Mockito
        .when(
          proposalCoordinatorService
            .unvote(UnvoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None))
        )
        .thenReturn(Future.failed(new IllegalArgumentException("You shall not, vote!")))

      whenReady(
        proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    scenario("unvote failed on lock not acquired") {
      val sessionId = SessionId("unvote-failed-on-lock-not-acquired")
      val proposalId = ProposalId("unvote-failed-on-lock-not-acquired")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.failed(ConcurrentModification("talk to my hand")))

      whenReady(
        proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never()).unlockSessionForVote(sessionId, proposalId)
        verify(proposalCoordinatorService, never()).unvote(
          UnvoteProposalCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            maybeOrganisationId = None,
            vote = None
          )
        )
      }
    }
  }

  feature("qualify proposal") {
    scenario("successful qualification") {
      val sessionId = SessionId("successful-qualification")
      val proposalId = ProposalId("successful-qualification")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(
          sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)
        )
        .thenReturn(Future.successful {})

      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      Mockito
        .when(
          proposalCoordinatorService.qualification(
            QualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None
            )
          )
        )
        .thenReturn(Future.successful(Some(Qualification(QualificationKey.LikeIt, 1))))

      whenReady(
        proposalService.qualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    scenario("qualification failed") {
      val sessionId = SessionId("qualification-failed")
      val proposalId = ProposalId("qualification-failed")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(
          sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)
        )
        .thenReturn(Future.successful {})

      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      Mockito
        .when(
          proposalCoordinatorService.qualification(
            QualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None
            )
          )
        )
        .thenReturn(Future.failed(new IllegalStateException("Thou shall not qualify!")))

      whenReady(
        proposalService.qualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    scenario("failed to acquire qualification lock") {
      val sessionId = SessionId("failed-to-acquire-qualification-lock")
      val proposalId = ProposalId("failed-to-acquire-qualification-lock")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(
          sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)
        )
        .thenReturn(Future.failed(ConcurrentModification("Try again later")))

      whenReady(
        proposalService.qualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never())
          .unlockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)

        verify(proposalCoordinatorService, never())
          .qualification(
            QualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None
            )
          )
      }
    }
  }

  feature("remove qualification") {
    scenario("successful qualification removal") {
      val sessionId = SessionId("successful-qualification-removal")
      val proposalId = ProposalId("successful-qualification-removal")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(
          sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)
        )
        .thenReturn(Future.successful {})

      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      Mockito
        .when(
          proposalCoordinatorService.unqualification(
            UnqualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None
            )
          )
        )
        .thenReturn(Future.successful(Some(Qualification(QualificationKey.LikeIt, 1))))

      whenReady(
        proposalService.unqualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    scenario("qualification removal failed") {
      val sessionId = SessionId("qualification-removal-failed")
      val proposalId = ProposalId("qualification-removal-failed")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(
          sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)
        )
        .thenReturn(Future.successful {})

      Mockito
        .when(
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
        )
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      Mockito
        .when(
          proposalCoordinatorService.unqualification(
            UnqualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None
            )
          )
        )
        .thenReturn(Future.failed(new IllegalStateException("Thou shall not qualify!")))

      whenReady(
        proposalService.unqualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    scenario("failed to acquire qualification removal lock") {
      val sessionId = SessionId("failed-to-acquire-qualification-removal-lock")
      val proposalId = ProposalId("failed-to-acquire-qualification-removal-lock")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      Mockito
        .when(
          sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)
        )
        .thenReturn(Future.failed(ConcurrentModification("Try again later")))

      whenReady(
        proposalService.unqualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never())
          .unlockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)

        verify(proposalCoordinatorService, never())
          .unqualification(
            UnqualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None
            )
          )
      }
    }
  }

}
