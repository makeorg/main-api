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

import java.time.{LocalDate, ZonedDateTime}

import akka.actor.ActorSystem
import cats.data.NonEmptyList
import com.sksamuel.elastic4s.searches.sort.SortOrder
import org.make.api.idea._
import org.make.api.question.{AuthorRequest, QuestionService, QuestionServiceComponent}
import org.make.api.segment.{SegmentService, SegmentServiceComponent}
import org.make.api.semantic._
import org.make.api.sessionhistory._
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.tagtype.{TagTypeService, TagTypeServiceComponent}
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical._
import org.make.api.technical.security.{SecurityConfiguration, SecurityConfigurationComponent, SecurityHelper}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest, TestUtils}
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions._
import org.make.core.history.HistoryActions.VoteTrust._
import org.make.core.idea.{IdeaId, IdeaMapping, IdeaMappingId}
import org.make.core.operation.OperationId
import org.make.core.profile.Profile
import org.make.core.proposal.ProposalStatus.{Accepted, Pending}
import org.make.core.proposal.QualificationKey.{
  DoNotCare,
  DoNotUnderstand,
  Doable,
  Impossible,
  LikeIt,
  NoOpinion,
  NoWay,
  PlatitudeAgree,
  PlatitudeDisagree
}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId, TopProposalsMode}
import org.make.core.reference.{Country, Language}
import org.make.core.session.SessionId
import org.make.core.tag._
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationFailedError}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.make.core.proposal.Vote

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.make.api.technical.crm.QuestionResolver
import org.make.core.technical.IdGenerator

class ProposalServiceTest
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
    with SecurityConfigurationComponent
    with SegmentServiceComponent {

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
  override val segmentService: SegmentService = mock[SegmentService]

  when(segmentService.resolveSegment(any[RequestContext])).thenReturn(Future.successful(None))

  when(securityConfiguration.secureHashSalt).thenReturn("some-hashed-supposed-to-be-secure")

  def generateHash(proposalId: ProposalId, requestContext: RequestContext): String = {
    SecurityHelper.generateProposalKeyHash(
      proposalId,
      requestContext.sessionId,
      requestContext.location,
      securityConfiguration.secureVoteSalt
    )
  }

  when(userService.getUsersByUserIds(Seq.empty))
    .thenReturn(Future.successful(Seq.empty))

  when(tagTypeService.findAll())
    .thenReturn(
      Future.successful(
        Seq(
          TagType(TagTypeId("stake"), "stake", TagTypeDisplay.Displayed, requiredForEnrichment = true),
          TagType(TagTypeId("solution-type"), "Solution Type", TagTypeDisplay.Displayed, requiredForEnrichment = false),
          TagType(TagTypeId("other"), "Other", TagTypeDisplay.Displayed, requiredForEnrichment = false)
        )
      )
    )

  when(tagService.findByTagIds(Seq.empty))
    .thenReturn(Future.successful(Seq.empty))

  val moderatorId: UserId = UserId("moderator-id")

  private val moderator = TestUtils.user(
    id = moderatorId,
    email = "moderator@make.org",
    firstName = Some("mode"),
    lastName = Some("rator"),
    roles = Seq(Role.RoleCitizen, Role.RoleModerator)
  )

  when(userService.getUser(moderatorId))
    .thenReturn(Future.successful(Some(moderator)))

  def user(id: UserId): User = {
    val idString = id.value
    TestUtils.user(id = id, email = s"$idString@make.org", firstName = Some(idString))
  }

  def simpleProposal(id: ProposalId): Proposal = {
    TestUtils.proposal(
      id = id,
      content = s"proposal with id ${id.value}",
      author = UserId(s"user-${id.value}"),
      status = Pending,
      createdAt = Some(DateHelper.now())
    )
  }

  when(sessionHistoryCoordinatorService.unlockSessionForVote(any[SessionId], any[ProposalId]))
    .thenReturn(Future.successful {})

  when(
    sessionHistoryCoordinatorService
      .unlockSessionForQualification(any[SessionId], any[ProposalId], any[QualificationKey])
  ).thenReturn(Future.successful {})

  Feature("next proposal to moderate") {

    def searchQuery(question: String): SearchQuery = SearchQuery(
      filters = Some(
        SearchFilters(
          question = Some(QuestionSearchFilter(Seq(QuestionId(question)))),
          status = Some(StatusSearchFilter(Seq(ProposalStatus.Pending)))
        )
      ),
      sort = Some(Sort(Some(ProposalElasticsearchFieldNames.createdAt), Some(SortOrder.Asc))),
      limit = Some(50),
      sortAlgorithm = Some(B2BFirstAlgorithm)
    )

    Scenario("no proposal matching criteria") {

      val question = "next proposal to moderate - no proposal matching criteria"

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(Future.successful(ProposalsSearchResult(total = 0, results = Seq.empty)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
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

    Scenario("no proposal can be locked") {
      val question = "next proposal to moderate - no proposal can be locked"

      val proposal1 =
        proposal(
          id = ProposalId(s"$question-unlockable-1"),
          author = UserId(s"$question-user-unlockable-1"),
          status = Pending
        )
      val proposal2 =
        proposal(
          id = ProposalId(s"$question-unlockable-2"),
          author = UserId(s"$question-user-unlockable-2"),
          status = Pending
        )
      val proposal3 =
        proposal(
          id = ProposalId(s"$question-unlockable-3"),
          author = UserId(s"$question-user-unlockable-3"),
          status = Pending
        )
      val proposal4 =
        proposal(
          id = ProposalId(s"$question-unlockable-4"),
          author = UserId(s"$question-user-unlockable-4"),
          status = Pending
        )

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 4,
                results = Seq(
                  indexedProposal(proposal1.proposalId),
                  indexedProposal(proposal2.proposalId),
                  indexedProposal(proposal3.proposalId),
                  indexedProposal(proposal4.proposalId)
                )
              )
            )
        )

      Seq(proposal1, proposal2, proposal3, proposal4).foreach { proposal =>
        when(
          proposalCoordinatorService.lock(
            eqTo(LockProposalCommand(proposal.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
          )
        ).thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

        when(proposalCoordinatorService.getProposal(proposal.proposalId))
          .thenReturn(Future.successful(Some(proposal)))

        when(userService.getUser(proposal.author))
          .thenReturn(Future.successful(Some(user(proposal.author))))
      }

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          questionId = QuestionId(question),
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

    Scenario("second proposal can be locked") {
      val question = "next proposal to moderate - second proposal can be locked"

      val unlockable = proposal(
        id = ProposalId(s"$question-unlockable"),
        author = UserId(s"$question-user-unlockable"),
        status = Pending
      )

      val lockable =
        proposal(id = ProposalId(s"$question-lockable"), author = UserId(s"$question-user-lockable"), status = Pending)

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 4,
                results = Seq(
                  indexedProposal(unlockable.proposalId),
                  indexedProposal(lockable.proposalId),
                  indexedProposal(ProposalId("ignored-1")),
                  indexedProposal(ProposalId("ignored-2"))
                )
              )
            )
        )

      when(
        proposalCoordinatorService.lock(
          eqTo(LockProposalCommand(unlockable.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
        )
      ).thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      when(
        proposalCoordinatorService.lock(
          eqTo(LockProposalCommand(lockable.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
        )
      ).thenReturn(Future.successful(Some(moderatorId)))

      Seq(unlockable, lockable).foreach { proposal =>
        when(proposalCoordinatorService.getProposal(eqTo(proposal.proposalId)))
          .thenReturn(Future.successful(Some(proposal)))

        when(userService.getUser(proposal.author))
          .thenReturn(Future.successful(Some(user(proposal.author))))
      }

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(defined)
        maybeProposal.map(_.proposalId) should contain(lockable.proposalId)
      }
    }

    Scenario("first proposal is already moderated") {
      val question = "next proposal to moderate - first proposal is already moderated"

      val moderated = proposal(
        id = ProposalId(s"$question-moderated"),
        author = UserId(s"$question-user-moderated"),
        status = Accepted
      )

      val lockable =
        proposal(id = ProposalId(s"$question-lockable"), author = UserId(s"$question-user-lockable"), status = Pending)

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 4,
                results = Seq(
                  indexedProposal(moderated.proposalId),
                  indexedProposal(lockable.proposalId),
                  indexedProposal(ProposalId("ignored-1")),
                  indexedProposal(ProposalId("ignored-2"))
                )
              )
            )
        )

      Seq(moderated, lockable).foreach { proposal =>
        when(proposalCoordinatorService.getProposal(eqTo(proposal.proposalId)))
          .thenReturn(Future.successful(Some(proposal)))

        when(userService.getUser(proposal.author))
          .thenReturn(Future.successful(Some(user(proposal.author))))

        when(
          proposalCoordinatorService.lock(
            eqTo(LockProposalCommand(proposal.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
          )
        ).thenReturn(Future.successful(Some(moderatorId)))
      }

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(defined)
        maybeProposal.map(_.proposalId) should contain(lockable.proposalId)
      }

    }

    Scenario("first proposal can be locked") {
      val question = "next proposal to moderate - first proposal can be locked"

      val lockable =
        proposal(id = ProposalId(s"$question-lockable"), author = UserId(s"$question-user-lockable"), status = Pending)

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(ProposalsSearchResult(total = 1, results = Seq(indexedProposal(lockable.proposalId))))
        )

      when(
        proposalCoordinatorService.lock(
          eqTo(LockProposalCommand(lockable.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
        )
      ).thenReturn(Future.successful(Some(moderatorId)))

      when(proposalCoordinatorService.getProposal(lockable.proposalId))
        .thenReturn(Future.successful(Some(lockable)))

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      when(userService.getUser(lockable.author))
        .thenReturn(Future.successful(Some(user(lockable.author))))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = false,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(defined)
        maybeProposal.map(_.proposalId) should contain(lockable.proposalId)
      }

    }
  }

  Feature("next proposal to enrich") {

    def searchQuery(question: String): SearchQuery = SearchQuery(
      filters = Some(
        SearchFilters(
          question = Some(QuestionSearchFilter(Seq(QuestionId(question)))),
          status = Some(StatusSearchFilter(Seq(ProposalStatus.Accepted))),
          toEnrich = Some(ToEnrichSearchFilter(true))
        )
      ),
      sort = Some(Sort(Some(ProposalElasticsearchFieldNames.createdAt), Some(SortOrder.Asc))),
      limit = Some(50),
      sortAlgorithm = Some(B2BFirstAlgorithm)
    )

    val stake: TagType = TagType(
      tagTypeId = TagTypeId("stake"),
      label = "stake haché",
      display = TagTypeDisplay.Displayed,
      requiredForEnrichment = true
    )
    val otherTagType: TagType = TagType(
      tagTypeId = TagTypeId("other"),
      label = "other",
      display = TagTypeDisplay.Displayed,
      requiredForEnrichment = false
    )

    val tagTypes = Seq(stake, otherTagType)

    when(tagTypeService.findAll(requiredForEnrichmentFilter = Some(true)))
      .thenReturn(Future.successful(tagTypes))

    when(tagService.findByTagIds(Seq.empty[TagId]))
      .thenReturn(Future.successful(Seq.empty[Tag]))

    Scenario("no proposal matching criteria") {
      val question = "next proposal to enrich - no proposal matching criteria"

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(Future.successful(ProposalsSearchResult(total = 0, results = Seq.empty)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = true,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(None)
      }
    }

    Scenario("no proposal can be locked") {
      val question = "next proposal to enrich - no proposal can be locked"

      val proposal1 = proposal(
        ProposalId(s"$question-unlockable-1"),
        author = UserId(s"$question-user-unlockable-1"),
        status = Accepted
      )
      val proposal2 = proposal(
        ProposalId(s"$question-unlockable-2"),
        author = UserId(s"$question-user-unlockable-2"),
        status = Accepted
      )
      val proposal3 = proposal(
        ProposalId(s"$question-unlockable-3"),
        author = UserId(s"$question-user-unlockable-3"),
        status = Accepted
      )
      val proposal4 = proposal(
        ProposalId(s"$question-unlockable-4"),
        author = UserId(s"$question-user-unlockable-4"),
        status = Accepted
      )

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 4,
                results = Seq(
                  indexedProposal(proposal1.proposalId),
                  indexedProposal(proposal2.proposalId),
                  indexedProposal(proposal3.proposalId),
                  indexedProposal(proposal4.proposalId)
                )
              )
            )
        )

      Seq(proposal1, proposal2, proposal3, proposal4).foreach { proposal =>
        when(proposalCoordinatorService.getProposal(proposal.proposalId))
          .thenReturn(Future.successful(Some(proposal)))

        when(
          proposalCoordinatorService.lock(
            eqTo(LockProposalCommand(proposal.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
          )
        ).thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

        when(userService.getUser(proposal.author))
          .thenReturn(Future.successful(Some(user(proposal.author))))

      }

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          questionId = QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = true,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(None)
      }

    }

    Scenario("second proposal can be locked") {
      val question = "next proposal to enrich - second proposal can be locked"

      val unlockable =
        proposal(
          ProposalId(s"$question - unlockable"),
          author = UserId(s"$question-user-unlockable"),
          status = Accepted
        )
      val lockable =
        proposal(ProposalId(s"$question - lockable"), author = UserId(s"$question-user-lockable"), status = Accepted)

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 4,
                results = Seq(
                  indexedProposal(unlockable.proposalId),
                  indexedProposal(lockable.proposalId),
                  indexedProposal(ProposalId("ignored-1")),
                  indexedProposal(ProposalId("ignored-2"))
                )
              )
            )
        )

      when(
        proposalCoordinatorService.lock(
          eqTo(LockProposalCommand(unlockable.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
        )
      ).thenReturn(Future.failed(ValidationFailedError(Seq.empty)))

      when(
        proposalCoordinatorService.lock(
          eqTo(LockProposalCommand(lockable.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
        )
      ).thenReturn(Future.successful(Some(moderatorId)))

      when(proposalCoordinatorService.getProposal(eqTo(unlockable.proposalId)))
        .thenReturn(Future.successful(Some(unlockable)))

      when(proposalCoordinatorService.getProposal(lockable.proposalId))
        .thenReturn(Future.successful(Some(lockable)))

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      Seq(lockable, unlockable).foreach { proposal =>
        when(userService.getUser(proposal.author))
          .thenReturn(Future.successful(Some(user(proposal.author))))
      }

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = true,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(defined)
        maybeProposal.map(_.proposalId) should contain(lockable.proposalId)
      }

    }

    Scenario("first proposal is already enriched") {
      val question = "next proposal to enrich - first proposal is already enriched"

      val tagId = TagId("a-stake-tag")
      val enriched =
        proposal(
          ProposalId(s"$question - enriched"),
          author = UserId(s"$question-user-enriched"),
          status = Accepted,
          tags = Seq(tagId)
        )
      val lockable =
        proposal(ProposalId(s"$question - lockable"), author = UserId(s"$question-user-lockable"), status = Accepted)

      when(tagService.findByTagIds(Seq(tagId)))
        .thenReturn(
          Future.successful(Seq(Tag(tagId, tagId.value, TagDisplay.Displayed, stake.tagTypeId, 0f, None, None)))
        )

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(
              ProposalsSearchResult(
                total = 4,
                results = Seq(
                  indexedProposal(enriched.proposalId),
                  indexedProposal(lockable.proposalId),
                  indexedProposal(ProposalId("ignored-1")),
                  indexedProposal(ProposalId("ignored-2"))
                )
              )
            )
        )

      Seq(enriched, lockable).foreach { proposal =>
        when(
          proposalCoordinatorService.lock(
            eqTo(LockProposalCommand(proposal.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
          )
        ).thenReturn(Future.successful(Some(moderatorId)))

        when(proposalCoordinatorService.getProposal(eqTo(proposal.proposalId)))
          .thenReturn(Future.successful(Some(proposal)))

        when(userService.getUser(proposal.author))
          .thenReturn(Future.successful(Some(user(proposal.author))))
      }

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = true,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(defined)
        maybeProposal.map(_.proposalId) should contain(lockable.proposalId)
      }

    }

    Scenario("first proposal can be locked") {
      val question = "lock-first"
      val lockable =
        proposal(ProposalId(s"$question-lockable"), author = UserId(s"$question-user-lockable"), status = Accepted)

      when(elasticsearchProposalAPI.searchProposals(searchQuery(question)))
        .thenReturn(
          Future
            .successful(ProposalsSearchResult(total = 1, results = Seq(indexedProposal(lockable.proposalId))))
        )

      when(
        proposalCoordinatorService.lock(
          eqTo(LockProposalCommand(lockable.proposalId, moderator.userId, moderator.fullName, RequestContext.empty))
        )
      ).thenReturn(Future.successful(Some(moderatorId)))

      when(proposalCoordinatorService.getProposal(lockable.proposalId))
        .thenReturn(Future.successful(Some(lockable)))

      when(userService.getUser(moderatorId))
        .thenReturn(Future.successful(Some(moderator)))

      when(userService.getUser(lockable.author))
        .thenReturn(Future.successful(Some(user(lockable.author))))

      whenReady(
        proposalService.searchAndLockProposalToModerate(
          QuestionId(question),
          moderatorId,
          RequestContext.empty,
          toEnrich = true,
          minVotesCount = None,
          minScore = None
        ),
        Timeout(3.seconds)
      ) { maybeProposal =>
        maybeProposal should be(defined)
        maybeProposal.map(_.proposalId) should contain(lockable.proposalId)
      }

    }
  }

  Feature("search proposals voted by user") {

    val paul: User = user(UserId("paul-user-id"))
    Scenario("user has no votes on the proposals") {
      when(
        userHistoryCoordinatorService
          .retrieveVotedProposals(eqTo(RequestUserVotedProposals(userId = paul.userId)))
      ).thenReturn(Future.successful(Seq.empty))

      when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      whenReady(
        proposalService.searchProposalsVotedByUser(
          userId = paul.userId,
          filterVotes = None,
          filterQualifications = None,
          sort = None,
          limit = None,
          skip = None,
          requestContext = RequestContext.empty
        ),
        Timeout(3.seconds)
      ) { proposalResultResponse =>
        proposalResultResponse.total should be(0)
        proposalResultResponse.results should be(Seq.empty)
      }
    }

    Scenario("user has votes on some proposals") {

      val gilProposal1 = indexedProposal(ProposalId("gil-1"))
      val gilProposal2 = indexedProposal(ProposalId("gil-2"))
      val gil: User = user(UserId("gil-user-id"))
      when(
        sessionHistoryCoordinatorService
          .retrieveVotedProposals(eqTo(RequestSessionVotedProposals(sessionId = SessionId("my-session"))))
      ).thenReturn(Future.successful(Seq(gilProposal1.id, gilProposal2.id)))
      when(
        userHistoryCoordinatorService
          .retrieveVotedProposals(eqTo(RequestUserVotedProposals(gil.userId)))
      ).thenReturn(Future.successful(Seq(gilProposal1.id, gilProposal2.id)))
      when(sessionHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestSessionVoteValues]))
        .thenReturn(
          Future.successful(
            Map(
              gilProposal2.id -> VoteAndQualifications(
                Agree,
                Map(LikeIt -> Trusted),
                ZonedDateTime.parse("2018-03-01T16:09:30.441Z"),
                Trusted
              ),
              gilProposal1.id -> VoteAndQualifications(
                Agree,
                Map(LikeIt -> Trusted),
                ZonedDateTime.parse("2018-03-02T16:09:30.441Z"),
                Trusted
              )
            )
          )
        )

      when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(
          Future.successful(
            Map(
              gilProposal2.id -> VoteAndQualifications(
                Agree,
                Map(LikeIt -> Trusted),
                ZonedDateTime.parse("2018-03-01T16:09:30.441Z"),
                Trusted
              ),
              gilProposal1.id -> VoteAndQualifications(
                Agree,
                Map(LikeIt -> Trusted),
                ZonedDateTime.parse("2018-03-02T16:09:30.441Z"),
                Trusted
              )
            )
          )
        )

      val query = SearchQuery(
        filters = Some(
          SearchFilters(proposal = Some(ProposalSearchFilter(proposalIds = Seq(gilProposal1.id, gilProposal2.id))))
        ),
        sort = None,
        limit = None,
        skip = None
      )

      when(elasticsearchProposalAPI.searchProposals(query))
        .thenReturn(Future.successful(ProposalsSearchResult(total = 2, results = Seq(gilProposal2, gilProposal1))))

      whenReady(
        proposalService.searchProposalsVotedByUser(
          userId = gil.userId,
          filterVotes = None,
          filterQualifications = None,
          sort = None,
          limit = None,
          skip = None,
          requestContext = RequestContext.empty.copy(sessionId = SessionId("my-session"))
        ),
        Timeout(3.seconds)
      ) { proposalsResultResponse =>
        proposalsResultResponse.total should be(2)
        proposalsResultResponse.results.head.id should be(gilProposal1.id)
        proposalsResultResponse.results.last.id should be(gilProposal2.id)
      }
    }
  }

  Feature("createInitialProposal") {
    Scenario("createInitialProposal") {

      val question =
        Question(
          QuestionId("createInitialProposal"),
          slug = "create-initial-proposal",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "how to create initial proposals?",
          shortTitle = None,
          operationId = None
        )

      when(userService.retrieveOrCreateVirtualUser(any[AuthorRequest], any[Country], any[Language]))
        .thenReturn(Future.successful(user(UserId("user"))))

      when(userService.getUser(any[UserId]))
        .thenReturn(Future.successful(Some(user(UserId("user")))))

      when(proposalCoordinatorService.propose(any[ProposeCommand]))
        .thenReturn(Future.successful(ProposalId("my-proposal")))

      when(proposalCoordinatorService.getProposal(ProposalId("my-proposal")))
        .thenReturn(Future.successful(Some(simpleProposal(ProposalId("my-proposal")))))

      when(ideaMappingService.getOrCreateMapping(question.questionId, None, None))
        .thenReturn(
          Future.successful(IdeaMapping(IdeaMappingId("mapping"), question.questionId, None, None, IdeaId("my-idea")))
        )

      when(tagService.findByTagIds(Seq(TagId("my-tag"))))
        .thenReturn(Future.successful(Seq.empty))

      when(
        proposalCoordinatorService.accept(
          eqTo(
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
      ).thenReturn(Future.successful(None))

      when(tagService.findByTagIds(Seq(TagId("my-tag"))))
        .thenReturn(Future.successful(Seq.empty))

      val result = proposalService.createInitialProposal(
        "my content",
        question,
        Country("FR"),
        Seq(TagId("my-tag")),
        moderator = moderatorId,
        moderatorRequestContext = RequestContext.empty,
        author = AuthorRequest(None, "firstName", None, None, None)
      )

      whenReady(result, Timeout(5.seconds)) { proposalId =>
        proposalId.value should be("my-proposal")
      }
    }
  }

  Feature("get tags for proposal") {
    def tag(id: String): Tag = Tag(
      tagId = TagId(id),
      label = "label",
      display = TagDisplay.Inherit,
      tagTypeId = TagTypeId("tag-type-id"),
      weight = 1.0f,
      operationId = None,
      questionId = Some(QuestionId("question-id"))
    )
    def proposal(questionId: Option[QuestionId], tags: Seq[TagId]): Proposal = Proposal(
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

    Scenario("proposal without tags") {
      when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq(tag("id-1"), tag("id-2"), tag("id-3"))))
      when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(
          Future.successful(
            GetPredictedTagsResponse(
              tags = Seq(PredictedTag(TagId("id-3"), TagTypeId("tag-type"), "some-label", "tag-type-label", 0d)),
              modelName = "auto"
            )
          )
        )

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
    Scenario("proposal with tags") {
      when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq(tag("id-1"), tag("id-2"), tag("id-3"))))

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
        tag3.predicted shouldBe false

        tagsWithModel.modelName should be("none")
      }
    }
    Scenario("proposal without question") {
      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(None, Seq.empty))
      whenReady(result, Timeout(5.seconds)) { tagsForProposal =>
        tagsForProposal.tags.isEmpty shouldBe true
      }
    }
    Scenario("proposal without tags in question") {
      when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq.empty))
      when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(Future.successful(GetPredictedTagsResponse.none))

      val result: Future[TagsForProposalResponse] =
        proposalService.getTagsForProposal(proposal(Some(QuestionId("question-id")), Seq(TagId("id-2"))))
      whenReady(result, Timeout(5.seconds)) { tagsForProposal =>
        tagsForProposal.tags.isEmpty shouldBe true
      }
    }
    Scenario("semantic fails to return a result") {
      when(tagService.findByQuestionId(any[QuestionId]))
        .thenReturn(Future.successful(Seq(tag("id-1"), tag("id-2"), tag("id-3"))))
      when(semanticService.getPredictedTagsForProposal(any[Proposal]))
        .thenReturn(Future.failed(new RuntimeException("This is an expected exception.")))

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
        tag3.checked shouldBe false

        tag1.predicted shouldBe false
        tag2.predicted shouldBe false
        tag3.predicted shouldBe false

        tagsWithModel.modelName should be("none")
      }
    }
  }

  Feature("validate proposal") {
    val proposalId = ProposalId("validate-proposal")

    val question = Question(
      questionId = QuestionId("question-id"),
      slug = "question",
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      question = "question",
      shortTitle = None,
      operationId = None
    )

    val validatedProposal = simpleProposal(proposalId)

    when(ideaMappingService.getOrCreateMapping(question.questionId, None, None))
      .thenReturn(
        Future.successful(IdeaMapping(IdeaMappingId("mapping"), question.questionId, None, None, IdeaId("my-idea")))
      )

    when(proposalCoordinatorService.getProposal(proposalId))
      .thenReturn(Future.successful(Some(validatedProposal)))

    when(
      proposalCoordinatorService.accept(
        eqTo(
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
    ).thenReturn(Future.successful(Some(validatedProposal)))

    Scenario("validate without predicted tags") {
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

    Scenario("validate with predicted tags") {
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
          .publish(eqTo(PredictedTagsEvent(proposalId, Seq(TagId("predicted-tag-id")), Seq.empty, "auto")))
        maybeProposal.isDefined should be(true)
        maybeProposal.map(_.proposalId) should be(Some(proposalId))
      }
    }
  }

  Feature("update proposal") {
    Scenario("update proposal with both tags") {

      when(proposalCoordinatorService.getProposal(ProposalId("update-proposal")))
        .thenReturn(Future.successful(Some(simpleProposal(ProposalId("update-proposal")))))

      when(userService.getUser(UserId("user-update-proposal")))
        .thenReturn(Future.successful(Some(user(UserId("user-update-proposal")))))

      val tagIds = Seq(TagId("stake-1"), TagId("stake-2"), TagId("solution-1"), TagId("solution-2"), TagId("other"))

      when(tagService.findByTagIds(tagIds))
        .thenReturn(
          Future.successful(
            Seq(
              Tag(TagId("stake-1"), "stake 1", TagDisplay.Inherit, TagTypeId("stake"), 50.0f, None, None),
              Tag(TagId("stake-2"), "stake 2", TagDisplay.Inherit, TagTypeId("stake"), 80.0f, None, None),
              Tag(TagId("solution-1"), "solution 1", TagDisplay.Inherit, TagTypeId("solution-type"), 50.0f, None, None),
              Tag(
                TagId("solution-2"),
                "solution type 2",
                TagDisplay.Inherit,
                TagTypeId("solution-type"),
                20.0f,
                None,
                None
              ),
              Tag(TagId("other"), "other", TagDisplay.Inherit, TagTypeId("other"), 50.0f, None, None)
            )
          )
        )

      when(
        ideaMappingService
          .getOrCreateMapping(QuestionId("update-proposal"), Some(TagId("stake-2")), Some(TagId("solution-1")))
      ).thenReturn(
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
        NonEmptyList.of(Country("FR")),
        Language("fr"),
        "how to update a proposal?",
        shortTitle = None,
        None
      )

      when(
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
      ).thenReturn(
        Future.successful(
          Some(simpleProposal(ProposalId("update-proposal")).copy(tags = tagIds, idea = Some(IdeaId("update-idea"))))
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

    Scenario("update proposal when the moderator provides an idea, use it") {

      when(proposalCoordinatorService.getProposal(ProposalId("update-proposal-3")))
        .thenReturn(Future.successful(Some(simpleProposal(ProposalId("update-proposal-3")))))

      when(userService.getUser(UserId("user-update-proposal-3")))
        .thenReturn(Future.successful(Some(user(UserId("user-update-proposal-3")))))

      val tagIds = Seq(TagId("stake-1"), TagId("stake-2"), TagId("solution-1"), TagId("solution-2"), TagId("other"))

      val question = Question(
        QuestionId("update-proposal"),
        "update-proposal",
        NonEmptyList.of(Country("FR")),
        Language("fr"),
        "how to update a proposal?",
        shortTitle = None,
        None
      )

      when(
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
      ).thenReturn(
        Future.successful(
          Some(
            simpleProposal(ProposalId("update-proposal-3")).copy(tags = tagIds, idea = Some(IdeaId("moderator-idea")))
          )
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

    Scenario("update proposal with no stake tags") {

      when(proposalCoordinatorService.getProposal(ProposalId("update-proposal-4")))
        .thenReturn(Future.successful(Some(simpleProposal(ProposalId("update-proposal-4")))))

      when(userService.getUser(UserId("user-update-proposal-4")))
        .thenReturn(Future.successful(Some(user(UserId("user-update-proposal-4")))))

      val tagIds = Seq(TagId("solution-1"), TagId("solution-2"), TagId("other"))

      when(tagService.findByTagIds(tagIds))
        .thenReturn(
          Future.successful(
            Seq(
              Tag(TagId("solution-1"), "solution 1", TagDisplay.Inherit, TagTypeId("solution-type"), 50.0f, None, None),
              Tag(
                TagId("solution-2"),
                "solution type 2",
                TagDisplay.Inherit,
                TagTypeId("solution-type"),
                20.0f,
                None,
                None
              ),
              Tag(TagId("other"), "other", TagDisplay.Inherit, TagTypeId("other"), 50.0f, None, None)
            )
          )
        )

      when(
        ideaMappingService
          .getOrCreateMapping(QuestionId("update-proposal"), None, Some(TagId("solution-1")))
      ).thenReturn(
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
        NonEmptyList.of(Country("FR")),
        Language("fr"),
        "how to update a proposal?",
        shortTitle = None,
        None
      )

      when(
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
      ).thenReturn(
        Future.successful(
          Some(simpleProposal(ProposalId("update-proposal")).copy(tags = tagIds, idea = Some(IdeaId("update-idea-2"))))
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
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      question = "question",
      shortTitle = None,
      operationId = None
    )

    val now = DateHelper.now()

    val updatedProposal = simpleProposal(proposalId)

    when(proposalCoordinatorService.update(any[UpdateProposalCommand]))
      .thenReturn(Future.successful(Some(updatedProposal)))

    when(
      proposalCoordinatorService.update(
        eqTo(
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
    ).thenReturn(Future.successful(Some(updatedProposal)))

    Scenario("update without predicted tags") {
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

    Scenario("update with predicted tags") {
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
          .publish(eqTo(PredictedTagsEvent(proposalId, Seq(TagId("predicted-tag-id")), Seq.empty, "auto")))
        maybeProposal.isDefined should be(true)
        maybeProposal.map(_.proposalId) should be(Some(proposalId))
      }
    }
  }

  Feature("vote") {
    Scenario("vote ok unlogged") {
      val sessionId = SessionId("vote-ok")
      val proposalId = ProposalId("vote-ok")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      when(
        proposalCoordinatorService
          .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None, Trusted))
      ).thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, 1, 1, 1, Seq.empty))))

      val hash = generateHash(proposalId, requestContext)

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree, Some(hash)),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    Scenario("already voted unlogged") {
      val sessionId = SessionId("already-voted")
      val proposalId = ProposalId("already-voted")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)
      val votes = VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map(proposalId -> votes)))
      when(
        proposalCoordinatorService
          .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, Some(votes), Trusted))
      ).thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, 1, 1, 1, Seq.empty))))

      val hash = generateHash(proposalId, requestContext)

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree, Some(hash)),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }

    }

    Scenario("vote failed voted unlogged") {
      val sessionId = SessionId("vote-failed-voted-unlogged")
      val proposalId = ProposalId("vote-failed-voted-unlogged")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      when(
        proposalCoordinatorService
          .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None, Trusted))
      ).thenReturn(Future.failed(new IllegalArgumentException("You shall not, vote!")))

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree, None).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    Scenario("vote failed on lock not acquired") {
      val sessionId = SessionId("vote-failed-on-lock-not-acquired")
      val proposalId = ProposalId("vote-failed-on-lock-not-acquired")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.failed(ConcurrentModification("talk to my hand")))

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree, None).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never).unlockSessionForVote(sessionId, proposalId)
        verify(proposalCoordinatorService, never).vote(
          VoteProposalCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            maybeOrganisationId = None,
            vote = None,
            voteTrust = Trusted
          )
        )
      }
    }

    Scenario("vote with bad proposal key") {
      val sessionId = SessionId("vote-with-bad-vote-key")
      val proposalId = ProposalId("vote-with-bad-vote-key")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      when(
        proposalCoordinatorService
          .vote(VoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None, Troll))
      ).thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, 0, 0, 0, Seq.empty))))

      whenReady(
        proposalService.voteProposal(proposalId, None, requestContext, VoteKey.Agree, Some("fake")),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }
  }

  Feature("unvote") {
    Scenario("unvote ok unlogged") {
      val sessionId = SessionId("unvote-ok")
      val proposalId = ProposalId("unvote-ok")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      when(
        proposalCoordinatorService
          .unvote(UnvoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None, Trusted))
      ).thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, 1, 1, 1, Seq.empty))))

      val hash = generateHash(proposalId, requestContext)

      whenReady(
        proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree, Some(hash)),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    Scenario("already unvoted unlogged") {
      val sessionId = SessionId("already-unvoted")
      val proposalId = ProposalId("already-unvoted")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)
      val votes = VoteAndQualifications(VoteKey.Agree, Map.empty, DateHelper.now(), Trusted)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map(proposalId -> votes)))
      when(
        proposalCoordinatorService
          .unvote(UnvoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, Some(votes), Trusted))
      ).thenReturn(Future.successful(Some(Vote(VoteKey.Agree, 1, 1, 1, 1, Seq.empty))))

      val hash = generateHash(proposalId, requestContext)

      whenReady(
        proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree, Some(hash)),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }

    }

    Scenario("unvote failed unvoted unlogged") {
      val sessionId = SessionId("unvote-failed-unvoted-unlogged")
      val proposalId = ProposalId("unvote-failed-unvoted-unlogged")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.successful {})
      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))
      when(
        proposalCoordinatorService
          .unvote(UnvoteProposalCommand(proposalId, None, requestContext, VoteKey.Agree, None, None, Trusted))
      ).thenReturn(Future.failed(new IllegalArgumentException("You shall not, vote!")))

      whenReady(
        proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree, None).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForVote(sessionId, proposalId)
      }
    }

    Scenario("unvote failed on lock not acquired") {
      val sessionId = SessionId("unvote-failed-on-lock-not-acquired")
      val proposalId = ProposalId("unvote-failed-on-lock-not-acquired")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForVote(sessionId, proposalId))
        .thenReturn(Future.failed(ConcurrentModification("talk to my hand")))

      whenReady(
        proposalService.unvoteProposal(proposalId, None, requestContext, VoteKey.Agree, None).failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never).unlockSessionForVote(sessionId, proposalId)
        verify(proposalCoordinatorService, never).unvote(
          UnvoteProposalCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            maybeOrganisationId = None,
            vote = None,
            voteTrust = Trusted
          )
        )
      }
    }
  }

  Feature("qualify proposal") {
    Scenario("successful qualification") {
      val sessionId = SessionId("successful-qualification")
      val proposalId = ProposalId("successful-qualification")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt))
        .thenReturn(Future.successful {})

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      when(
        proposalCoordinatorService.qualification(
          QualifyVoteCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            qualificationKey = QualificationKey.LikeIt,
            vote = None,
            voteTrust = Trusted
          )
        )
      ).thenReturn(Future.successful(Some(Qualification(QualificationKey.LikeIt, 1, 1, 1, 1))))

      val hash = generateHash(proposalId, requestContext)

      whenReady(
        proposalService
          .qualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt, Some(hash)),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    Scenario("qualification failed") {
      val sessionId = SessionId("qualification-failed")
      val proposalId = ProposalId("qualification-failed")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt))
        .thenReturn(Future.successful {})

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      when(
        proposalCoordinatorService.qualification(
          QualifyVoteCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            qualificationKey = QualificationKey.LikeIt,
            vote = None,
            voteTrust = Trusted
          )
        )
      ).thenReturn(Future.failed(new IllegalStateException("Thou shall not qualify!")))

      whenReady(
        proposalService
          .qualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt, None)
          .failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    Scenario("failed to acquire qualification lock") {
      val sessionId = SessionId("failed-to-acquire-qualification-lock")
      val proposalId = ProposalId("failed-to-acquire-qualification-lock")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt))
        .thenReturn(Future.failed(ConcurrentModification("Try again later")))

      whenReady(
        proposalService
          .qualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt, None)
          .failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never)
          .unlockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)

        verify(proposalCoordinatorService, never)
          .qualification(
            QualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None,
              voteTrust = Trusted
            )
          )
      }
    }
  }

  Feature("remove qualification") {
    Scenario("successful qualification removal") {
      val sessionId = SessionId("successful-qualification-removal")
      val proposalId = ProposalId("successful-qualification-removal")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId))))

      when(sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt))
        .thenReturn(Future.successful {})

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      when(
        proposalCoordinatorService.unqualification(
          UnqualifyVoteCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            qualificationKey = QualificationKey.LikeIt,
            vote = None,
            voteTrust = Trusted
          )
        )
      ).thenReturn(Future.successful(Some(Qualification(QualificationKey.LikeIt, 1, 1, 1, 1))))

      val hash = generateHash(proposalId, requestContext)

      whenReady(
        proposalService
          .unqualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt, Some(hash)),
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    Scenario("qualification removal failed") {
      val sessionId = SessionId("qualification-removal-failed")
      val proposalId = ProposalId("qualification-removal-failed")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt))
        .thenReturn(Future.successful {})

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(RequestSessionVoteValues(sessionId, Seq(proposalId)))
      ).thenReturn(Future.successful(Map.empty[ProposalId, VoteAndQualifications]))

      when(
        proposalCoordinatorService.unqualification(
          UnqualifyVoteCommand(
            proposalId = proposalId,
            maybeUserId = None,
            requestContext = requestContext,
            voteKey = VoteKey.Agree,
            qualificationKey = QualificationKey.LikeIt,
            vote = None,
            voteTrust = Trusted
          )
        )
      ).thenReturn(Future.failed(new IllegalStateException("Thou shall not qualify!")))

      whenReady(
        proposalService
          .unqualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt, None)
          .failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService).unlockSessionForQualification(
          sessionId,
          proposalId,
          QualificationKey.LikeIt
        )
      }
    }

    Scenario("failed to acquire qualification removal lock") {
      val sessionId = SessionId("failed-to-acquire-qualification-removal-lock")
      val proposalId = ProposalId("failed-to-acquire-qualification-removal-lock")
      val requestContext = RequestContext.empty.copy(sessionId = sessionId)

      when(sessionHistoryCoordinatorService.lockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt))
        .thenReturn(Future.failed(ConcurrentModification("Try again later")))

      whenReady(
        proposalService
          .unqualifyVote(proposalId, None, requestContext, VoteKey.Agree, QualificationKey.LikeIt, None)
          .failed,
        Timeout(5.seconds)
      ) { _ =>
        verify(sessionHistoryCoordinatorService, never)
          .unlockSessionForQualification(sessionId, proposalId, QualificationKey.LikeIt)

        verify(proposalCoordinatorService, never)
          .unqualification(
            UnqualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = None,
              requestContext = requestContext,
              voteKey = VoteKey.Agree,
              qualificationKey = QualificationKey.LikeIt,
              vote = None,
              voteTrust = Trusted
            )
          )
      }
    }
  }

  Feature("trust resolution") {

    def key(proposalId: ProposalId, requestContext: RequestContext): String = SecurityHelper.generateProposalKeyHash(
      proposalId,
      requestContext.sessionId,
      requestContext.location,
      securityConfiguration.secureVoteSalt
    )

    Scenario("in segment") {
      val proposalId = ProposalId("in segment")
      val requestContext = RequestContext.empty.copy(location = Some("sequence 123456"))

      proposalService.resolveVoteTrust(
        Some(key(proposalId, requestContext)),
        proposalId,
        Some("segment"),
        Some("segment"),
        requestContext
      ) should be(Segment)
    }

    Scenario("segments do not match") {
      val proposalId = ProposalId("segments do not match")
      val requestContext = RequestContext.empty.copy(location = Some("sequence 123456"))

      proposalService.resolveVoteTrust(
        Some(key(proposalId, requestContext)),
        proposalId,
        Some("user-segment"),
        Some("proposal-segment"),
        requestContext
      ) should be(Sequence)
    }

    Scenario("no segment") {
      val proposalId = ProposalId("no segment")
      val requestContext = RequestContext.empty.copy(location = Some("sequence 123456"))

      proposalService.resolveVoteTrust(Some(key(proposalId, requestContext)), proposalId, None, None, requestContext) should be(
        Sequence
      )
    }

    Scenario("not in sequence") {
      val proposalId = ProposalId("not in sequence")
      val requestContext = RequestContext.empty.copy(location = Some("operation-page 123456"))

      proposalService
        .resolveVoteTrust(Some(key(proposalId, requestContext)), proposalId, None, None, requestContext) should be(
        Trusted
      )
    }

    Scenario("not in sequence but in segment") {
      val proposalId = ProposalId("not in sequence but in segment")
      val requestContext = RequestContext.empty.copy(location = Some("operation-page 123456"))

      proposalService
        .resolveVoteTrust(
          Some(key(proposalId, requestContext)),
          proposalId,
          Some("segment"),
          Some("segment"),
          requestContext
        ) should be(Trusted)
    }

    Scenario("bad proposal key") {
      val proposalId = ProposalId("bad proposal key")
      val requestContext = RequestContext.empty.copy(location = Some("sequence 123456"))

      proposalService
        .resolveVoteTrust(Some("I am a troll"), proposalId, Some("segment"), Some("segment"), requestContext) should be(
        Troll
      )
    }

    Scenario("no proposal key") {
      val proposalId = ProposalId("no proposal key")
      val requestContext = RequestContext.empty.copy(location = Some("sequence 123456"))

      proposalService
        .resolveVoteTrust(None, proposalId, Some("segment"), Some("segment"), requestContext) should be(Troll)
    }
  }

  Feature("search for user") {
    Scenario("test all") {
      val unvoted: IndexedProposal = {
        val tmp = indexedProposal(ProposalId("unvoted"))
        tmp.copy(author = tmp.author.copy(anonymousParticipation = true))
      }

      when(elasticsearchProposalAPI.searchProposals(SearchQuery()))
        .thenReturn(
          Future
            .successful(ProposalsSearchResult(total = 2, results = Seq(indexedProposal(ProposalId("voted")), unvoted)))
        )

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(
            RequestSessionVoteValues(SessionId("my-session"), Seq(ProposalId("voted"), ProposalId("unvoted")))
          )
      ).thenReturn(
        Future
          .successful(Map(ProposalId("voted") -> VoteAndQualifications(Agree, Map.empty, DateHelper.now(), Trusted)))
      )

      val search = proposalService.searchForUser(
        None,
        SearchQuery(),
        RequestContext.empty.copy(sessionId = SessionId("my-session"))
      )

      whenReady(search, Timeout(5.seconds)) { result =>
        result.results.size should be(2)
        result.results.head.votes.filter(_.voteKey == Agree).map(_.hasVoted) should contain(true)
        result.results.tail.flatMap(_.votes).filter(_.voteKey == Agree).map(_.hasVoted) should contain(false)

        val nonemptyAuthor = result.results.head.author
        nonemptyAuthor.firstName should be(defined)
        nonemptyAuthor.postalCode should be(defined)
        nonemptyAuthor.age should be(defined)
        nonemptyAuthor.avatarUrl should be(defined)

        val emptyAuthor = result.results.last.author
        emptyAuthor.organisationSlug should be(empty)
        emptyAuthor.organisationName should be(empty)
        emptyAuthor.firstName should be(empty)
        emptyAuthor.postalCode should be(empty)
        emptyAuthor.age should be(empty)
        emptyAuthor.avatarUrl should be(empty)

      }
    }
  }

  Feature("get top proposals") {

    Scenario("get top proposals by stake tag") {

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(
            RequestSessionVoteValues(SessionId("my-session"), Seq(ProposalId("voted"), ProposalId("no-vote")))
          )
      ).thenReturn(
        Future
          .successful(Map(ProposalId("voted") -> VoteAndQualifications(Agree, Map.empty, DateHelper.now(), Trusted)))
      )

      when(
        elasticsearchProposalAPI
          .getTopProposals(QuestionId("question-id"), size = 10, ProposalElasticsearchFieldNames.selectedStakeTagId)
      ).thenReturn(Future.successful(Seq(indexedProposal(ProposalId("voted")), indexedProposal(ProposalId("no-vote")))))

      val topProposals = proposalService.getTopProposals(
        None,
        QuestionId("question-id"),
        size = 10,
        None,
        RequestContext.empty.copy(sessionId = SessionId("my-session"))
      )

      whenReady(topProposals, Timeout(5.seconds)) { result =>
        result.results.size should be(2)
        result.results.head.votes.filter(_.voteKey == Agree).map(_.hasVoted) should contain(true)
        result.results.tail.flatMap(_.votes).filter(_.voteKey == Agree).map(_.hasVoted) should contain(false)
      }
    }

    Scenario("get top proposals by idea") {

      when(
        sessionHistoryCoordinatorService
          .retrieveVoteAndQualifications(
            RequestSessionVoteValues(SessionId("my-session"), Seq(ProposalId("voted"), ProposalId("no-vote")))
          )
      ).thenReturn(
        Future
          .successful(Map(ProposalId("voted") -> VoteAndQualifications(Agree, Map.empty, DateHelper.now(), Trusted)))
      )

      when(
        elasticsearchProposalAPI
          .getTopProposals(QuestionId("question-id"), size = 10, ProposalElasticsearchFieldNames.ideaId)
      ).thenReturn(Future.successful(Seq(indexedProposal(ProposalId("voted")), indexedProposal(ProposalId("no-vote")))))

      val topProposals = proposalService.getTopProposals(
        maybeUserId = None,
        questionId = QuestionId("question-id"),
        size = 10,
        mode = Some(TopProposalsMode.IdeaMode),
        requestContext = RequestContext.empty.copy(sessionId = SessionId("my-session"))
      )

      whenReady(topProposals, Timeout(5.seconds)) { result =>
        result.results.size should be(2)
        result.results.head.votes.filter(_.voteKey == Agree).map(_.hasVoted) should contain(true)
        result.results.tail.flatMap(_.votes).filter(_.voteKey == Agree).map(_.hasVoted) should contain(false)
      }
    }
  }

  Feature("get moderation proposal by id") {
    Scenario("regular participation") {

      val proposalId = ProposalId("regular-participation")
      val author = {
        val baseUser = user(UserId("regular-participation"))
        baseUser.copy(
          firstName = Some("regular-participation-first-name"),
          lastName = Some("regular-participation-last-name"),
          organisationName = Some("regular-participation-organisation-name"),
          anonymousParticipation = false,
          profile = Profile.parseProfile(
            dateOfBirth = Some(LocalDate.parse("1998-01-01")),
            avatarUrl = Some("https://some-url"),
            postalCode = Some("12345")
          )
        )
      }

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId).copy(author = author.userId))))

      when(userService.getUser(author.userId))
        .thenReturn(Future.successful(Some(author)))

      whenReady(proposalService.getModerationProposalById(proposalId), Timeout(2.seconds)) { maybeProposal =>
        maybeProposal should be(defined)
        val proposal = maybeProposal.get
        proposal.author.firstName should contain("regular-participation-first-name")
        proposal.author.lastName should contain("regular-participation-last-name")
        proposal.author.postalCode should contain("12345")
        proposal.author.age.get >= 21 should be(true)
        proposal.author.organisationName should contain("regular-participation-organisation-name")
        proposal.author.organisationSlug should contain("regular-participation-organisation-name")
      }
    }
    Scenario("anonymous participation") {
      val proposalId = ProposalId("anonymous-participation")
      val author = {
        val baseUser = user(UserId("anonymous-participation"))
        baseUser.copy(
          firstName = Some("anonymous-participation-first-name"),
          lastName = Some("anonymous-participation-last-name"),
          organisationName = Some("anonymous-participation-organisation-name"),
          anonymousParticipation = true,
          profile = Profile.parseProfile(
            dateOfBirth = Some(LocalDate.parse("1998-01-01")),
            avatarUrl = Some("https://some-url"),
            postalCode = Some("12345")
          )
        )
      }

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(simpleProposal(proposalId).copy(author = author.userId))))

      when(userService.getUser(author.userId))
        .thenReturn(Future.successful(Some(author)))

      whenReady(proposalService.getModerationProposalById(proposalId), Timeout(2.seconds)) { maybeProposal =>
        maybeProposal should be(defined)
        val proposal = maybeProposal.get
        proposal.author.firstName should be(None)
        proposal.author.lastName should be(None)
        proposal.author.postalCode should be(None)
        proposal.author.age should be(None)
        proposal.author.organisationName should be(None)
        proposal.author.organisationSlug should be(None)
      }
    }
  }

  Feature("need reset votes") {
    val adminId = UserId("admin")
    val requestContext = RequestContext.empty

    Scenario("trolled proposal on votes") {
      val votes: Seq[Vote] = Seq(
        Vote(
          key = Agree,
          count = 1234,
          countVerified = 42,
          countSegment = 1,
          countSequence = 22,
          qualifications = Seq(
            Qualification(key = LikeIt, count = 10, countVerified = 10, countSequence = 1, countSegment = 1),
            Qualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
            Qualification(key = PlatitudeAgree, count = 4, countVerified = 4, countSequence = 0, countSegment = 2)
          )
        ),
        Vote(
          key = Disagree,
          count = 12,
          countVerified = 12,
          countSegment = 2,
          countSequence = 8,
          qualifications = Seq(
            Qualification(key = NoWay, count = 1, countVerified = 1, countSequence = 1, countSegment = 0),
            Qualification(key = Impossible, count = 3, countVerified = 3, countSequence = 1, countSegment = 2),
            Qualification(key = PlatitudeDisagree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
          )
        ),
        Vote(
          key = Neutral,
          count = 3,
          countVerified = 3,
          countSegment = 0,
          countSequence = 3,
          qualifications = Seq(
            Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
            Qualification(key = NoOpinion, count = 1, countVerified = 1, countSequence = 0, countSegment = 1),
            Qualification(key = DoNotCare, count = 1, countVerified = 1, countSequence = 1, countSegment = 0)
          )
        )
      )
      val proposal =
        TestUtils.proposal(id = ProposalId("trolled-votes"), status = ProposalStatus.Accepted, votes = votes)
      proposalService.needVoteReset(proposal) shouldBe true

      val updates = proposalService.updateCommand(adminId, requestContext, proposal.proposalId, proposal.votes)
      updates.votes.size shouldBe 1
      updates.votes.exists(_.key == Agree) shouldBe true
      updates.votes.head.count shouldBe Some(42)
      updates.votes.flatMap(_.qualifications).isEmpty shouldBe true
    }

    Scenario("trolled proposal on qualifications") {
      val votes: Seq[Vote] = Seq(
        Vote(
          key = Agree,
          count = 42,
          countVerified = 42,
          countSegment = 1,
          countSequence = 22,
          qualifications = Seq(
            Qualification(key = LikeIt, count = 10, countVerified = 10, countSequence = 1, countSegment = 1),
            Qualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
            Qualification(key = PlatitudeAgree, count = 4, countVerified = 4, countSequence = 0, countSegment = 2)
          )
        ),
        Vote(
          key = Disagree,
          count = 12,
          countVerified = 12,
          countSegment = 2,
          countSequence = 8,
          qualifications = Seq(
            Qualification(key = NoWay, count = 1000, countVerified = 1, countSequence = 1, countSegment = 0),
            Qualification(key = Impossible, count = 3, countVerified = 3, countSequence = 1, countSegment = 2),
            Qualification(key = PlatitudeDisagree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
          )
        ),
        Vote(
          key = Neutral,
          count = 3,
          countVerified = 3,
          countSegment = 0,
          countSequence = 3,
          qualifications = Seq(
            Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
            Qualification(key = NoOpinion, count = 1, countVerified = 1, countSequence = 0, countSegment = 1),
            Qualification(key = DoNotCare, count = 1, countVerified = 1, countSequence = 1, countSegment = 0)
          )
        )
      )
      val proposal =
        TestUtils.proposal(id = ProposalId("trolled-qualification"), status = ProposalStatus.Accepted, votes = votes)
      proposalService.needVoteReset(proposal) shouldBe true

      val updates = proposalService.updateCommand(adminId, requestContext, proposal.proposalId, proposal.votes)
      updates.votes.size shouldBe 1
      updates.votes.exists(_.key == Disagree) shouldBe true
      updates.votes.head.qualifications.exists(_.key == NoWay) shouldBe true
      updates.votes.head.qualifications.head.count shouldBe Some(1)
    }

    Scenario("not trolled proposal") {
      val votes: Seq[Vote] = Seq(
        Vote(
          key = Agree,
          count = 42,
          countVerified = 42,
          countSegment = 1,
          countSequence = 22,
          qualifications = Seq(
            Qualification(key = LikeIt, count = 10, countVerified = 10, countSequence = 1, countSegment = 1),
            Qualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
            Qualification(key = PlatitudeAgree, count = 4, countVerified = 4, countSequence = 0, countSegment = 2)
          )
        ),
        Vote(
          key = Disagree,
          count = 12,
          countVerified = 12,
          countSegment = 2,
          countSequence = 8,
          qualifications = Seq(
            Qualification(key = NoWay, count = 1, countVerified = 1, countSequence = 1, countSegment = 0),
            Qualification(key = Impossible, count = 3, countVerified = 3, countSequence = 1, countSegment = 2),
            Qualification(key = PlatitudeDisagree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
          )
        ),
        Vote(
          key = Neutral,
          count = 3,
          countVerified = 3,
          countSegment = 0,
          countSequence = 3,
          qualifications = Seq(
            Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
            Qualification(key = NoOpinion, count = 1, countVerified = 1, countSequence = 0, countSegment = 1),
            Qualification(key = DoNotCare, count = 1, countVerified = 1, countSequence = 1, countSegment = 0)
          )
        )
      )
      val proposal = TestUtils.proposal(id = ProposalId("correct"), status = ProposalStatus.Accepted, votes = votes)
      proposalService.needVoteReset(proposal) shouldBe false
    }

    Scenario("not accepted proposal") {
      val proposal = TestUtils.proposal(id = ProposalId("not-accepted"), status = ProposalStatus.Pending)
      proposalService.needVoteReset(proposal) shouldBe false
    }
  }

  Feature("resolve question from vote event") {
    val question = Question(
      questionId = QuestionId("some-question"),
      slug = "some-question",
      question = "?",
      shortTitle = None,
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      operationId = Some(OperationId("who cares?"))
    )

    val resolver = new QuestionResolver(Seq(question), Map.empty)

    Scenario("question resolved from request context") {
      val requestContext: RequestContext = RequestContext.empty.copy(questionId = Some(question.questionId))
      val proposalId: ProposalId = ProposalId("whatever")

      whenReady(proposalService.resolveQuestionFromVoteEvent(resolver, requestContext, proposalId), Timeout(3.seconds)) {
        _ should contain(question)
      }
    }

    Scenario("question resolved from proposal") {
      val requestContext: RequestContext = RequestContext.empty
      val proposalId: ProposalId = ProposalId("question resolved from proposal")

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(proposal(id = proposalId, questionId = question.questionId))))

      whenReady(proposalService.resolveQuestionFromVoteEvent(resolver, requestContext, proposalId), Timeout(3.seconds)) {
        _ should contain(question)
      }
    }

    Scenario("unable to resolve the question") {
      val requestContext: RequestContext = RequestContext.empty
      val proposalId: ProposalId = ProposalId("unable to resolve the question")

      when(proposalCoordinatorService.getProposal(proposalId))
        .thenReturn(Future.successful(Some(proposal(id = proposalId, questionId = QuestionId("other")))))

      whenReady(proposalService.resolveQuestionFromVoteEvent(resolver, requestContext, proposalId), Timeout(3.seconds)) {
        _ should be(None)
      }
    }
  }

  Feature("resolve question from proposal event") {
    val question = Question(
      questionId = QuestionId("some-question"),
      slug = "some-question",
      question = "?",
      shortTitle = None,
      countries = NonEmptyList.of(Country("FR")),
      language = Language("fr"),
      operationId = Some(OperationId("who cares?"))
    )

    val resolver = new QuestionResolver(Seq(question), Map.empty)

    Scenario("question resolved by request context") {
      val requestContext = RequestContext.empty.copy(questionId = Some(question.questionId))
      val userId = UserId("question resolved by request context")

      whenReady(
        proposalService.resolveQuestionFromUserProposal(resolver, requestContext, userId, DateHelper.now()),
        Timeout(2.seconds)
      ) {
        _ should contain(question)
      }
    }

    Scenario("user has no proposal") {
      val requestContext = RequestContext.empty
      val userId = UserId("user has no proposal")
      val date = DateHelper.now().minusHours(5)

      val filters =
        SearchFilters(user = Some(UserSearchFilter(userId)), status = Some(StatusSearchFilter(ProposalStatus.values)))

      when(
        elasticsearchProposalAPI
          .countProposals(SearchQuery(filters = Some(filters)))
      ).thenReturn(Future.successful(0L))

      whenReady(
        proposalService.resolveQuestionFromUserProposal(resolver, requestContext, userId, date),
        Timeout(2.seconds)
      ) {
        _ should be(None)
      }
    }

    Scenario("proposal found") {
      val requestContext = RequestContext.empty
      val userId = UserId("proposal found")
      val date = DateHelper.now().minusHours(5)

      val query = SearchQuery(filters = Some(
        SearchFilters(user = Some(UserSearchFilter(userId)), status = Some(StatusSearchFilter(ProposalStatus.values)))
      )
      )

      when(elasticsearchProposalAPI.countProposals(query))
        .thenReturn(Future.successful(2L))

      when(elasticsearchProposalAPI.searchProposals(query.copy(limit = Some(2)))).thenReturn {
        Future.successful(
          ProposalsSearchResult(
            total = 2L,
            results = Seq(
              indexedProposal(ProposalId("proposal-one")),
              indexedProposal(ProposalId("proposal-two"), questionId = question.questionId, createdAt = date)
            )
          )
        )
      }

      val result = proposalService.resolveQuestionFromUserProposal(resolver, requestContext, userId, date)
      whenReady(result, Timeout(2.seconds)) {
        _ should contain(question)
      }
    }

  }

}
