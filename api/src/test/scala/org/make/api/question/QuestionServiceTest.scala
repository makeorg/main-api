package org.make.api.question

import akka.actor.ActorSystem
import org.make.api.idea.topIdeaComments.{PersistentTopIdeaCommentService, PersistentTopIdeaCommentServiceComponent}
import org.make.api.idea.{
  PersistentTopIdeaService,
  PersistentTopIdeaServiceComponent,
  TopIdeaService,
  TopIdeaServiceComponent
}
import org.make.api.organisation.{OrganisationSearchEngine, OrganisationSearchEngineComponent}
import org.make.api.personality.{QuestionPersonalityService, QuestionPersonalityServiceComponent}
import org.make.api.proposal.{ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest, TestUtils}
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.personality.{Candidate, Personality, PersonalityId}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult, ProposalsAndVotesCountsByQuestion}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class QuestionServiceTest
    extends MakeUnitTest
    with DefaultQuestionService
    with PersistentQuestionServiceComponent
    with ActorSystemComponent
    with IdGeneratorComponent
    with QuestionPersonalityServiceComponent
    with UserServiceComponent
    with OrganisationSearchEngineComponent
    with TopIdeaServiceComponent
    with ProposalSearchEngineComponent
    with PersistentTopIdeaServiceComponent
    with PersistentTopIdeaCommentServiceComponent {

  override val actorSystem: ActorSystem = ActorSystem()
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val questionPersonalityService: QuestionPersonalityService = mock[QuestionPersonalityService]
  override val userService: UserService = mock[UserService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]
  override val elasticsearchOrganisationAPI: OrganisationSearchEngine = mock[OrganisationSearchEngine]
  override val topIdeaService: TopIdeaService = mock[TopIdeaService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val persistentTopIdeaService: PersistentTopIdeaService = mock[PersistentTopIdeaService]
  override val persistentTopIdeaCommentService: PersistentTopIdeaCommentService = mock[PersistentTopIdeaCommentService]

  val personalities: Seq[Personality] = Seq(
    Personality(
      personalityId = PersonalityId("personality-1"),
      userId = UserId("user-1"),
      questionId = QuestionId("question-id"),
      personalityRole = Candidate
    ),
    Personality(
      personalityId = PersonalityId("personality-2"),
      userId = UserId("user-2"),
      questionId = QuestionId("question-id"),
      personalityRole = Candidate
    )
  )

  val user1 = TestUtils.user(id = UserId("user-1"))
  val user2 = TestUtils.user(id = UserId("user-2"))

  feature("Get question personalities") {
    scenario("Get question personalities") {
      when(
        questionPersonalityService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = Some(QuestionId("question-id")),
          personalityRole = None
        )
      ).thenReturn(Future.successful(personalities))
      when(userService.getPersonality(UserId("user-1"))).thenReturn(Future.successful(Some(user1)))
      when(userService.getPersonality(UserId("user-2"))).thenReturn(Future.successful(Some(user2)))

      whenReady(
        questionService.getQuestionPersonalities(
          start = 0,
          end = None,
          questionId = QuestionId("question-id"),
          personalityRole = None
        ),
        Timeout(3.seconds)
      ) { questionPersonalities =>
        questionPersonalities.map(_.userId) should contain(UserId("user-1"))
        questionPersonalities.map(_.userId) should contain(UserId("user-2"))
      }
    }
  }

  feature("get question partners") {
    def newIndexedOrganisation(organisationId: String, scoreQuestion: Int) =
      IndexedOrganisation(
        UserId(organisationId),
        Some(organisationId),
        None,
        None,
        None,
        publicProfile = true,
        0,
        0,
        Language("fr"),
        Country("FR"),
        None,
        Seq(ProposalsAndVotesCountsByQuestion(QuestionId("question-id"), scoreQuestion, 0))
      )

    scenario("sort partners by counts") {
      when(
        elasticsearchOrganisationAPI.searchOrganisations(
          ArgumentMatchers.eq(
            OrganisationSearchQuery(
              filters = Some(
                OrganisationSearchFilters(
                  organisationIds =
                    Some(OrganisationIdsSearchFilter(Seq(UserId("organisation-1"), UserId("organisation-2"))))
                )
              ),
              sortAlgorithm = Some(ParticipationAlgorithm(QuestionId("question-id"))),
              limit = Some(1000),
              skip = Some(0)
            )
          )
        )
      ).thenReturn(
        Future.successful(
          OrganisationSearchResult(
            2L,
            Seq(newIndexedOrganisation("organisation-1", 2), newIndexedOrganisation("organisation-2", 42))
          )
        )
      )

      whenReady(
        questionService.getPartners(
          QuestionId("question-id"),
          Seq(UserId("organisation-1"), UserId("organisation-2")),
          Some(ParticipationAlgorithm(QuestionId("question-id"))),
          Some(42),
          None
        ),
        Timeout(3.seconds)
      ) { res =>
        res.total shouldBe 2L
        res.results.head.organisationId.value shouldBe "organisation-2"
        res.results(1).organisationId.value shouldBe "organisation-1"
      }
    }
  }

  feature("get top ideas") {
    scenario("get top ideas") {
      when(
        topIdeaService
          .search(start = 0, end = None, ideaId = None, questionIds = Some(Seq(QuestionId("question-id"))), name = None)
      ).thenReturn(
        Future
          .successful(
            Seq(
              TopIdea(
                TopIdeaId("top-idea-1"),
                IdeaId("idea-1"),
                QuestionId("question-id"),
                "top-idea-1",
                "label",
                TopIdeaScores(0, 0, 0),
                42
              ),
              TopIdea(
                TopIdeaId("top-idea-2"),
                IdeaId("idea-2"),
                QuestionId("question-id"),
                "top-idea-2",
                "label",
                TopIdeaScores(0, 0, 0),
                21
              )
            )
          )
      )

      when(persistentTopIdeaCommentService.countForAll(Seq(TopIdeaId("top-idea-1"), TopIdeaId("top-idea-2"))))
        .thenReturn(Future.successful(Map("top-idea-1" -> 5, "top-idea-2" -> 2)))

      when(elasticsearchProposalAPI.getRandomProposalsByIdeaWithAvatar(Seq(IdeaId("idea-1"), IdeaId("idea-2")), 1337))
        .thenReturn(
          Future.successful(
            Map(
              IdeaId("idea-1") ->
                AvatarsAndProposalsCount(
                  avatars = Seq("avatar-1", "avatar-2", "avatar-3", "avatar-4"),
                  proposalsCount = 42
                ),
              IdeaId("idea-2") -> AvatarsAndProposalsCount(
                avatars = Seq("avatar-5", "avatar-6", "avatar-7", "avatar-8"),
                proposalsCount = 21
              )
            )
          )
        )

      whenReady(
        questionService.getTopIdeas(start = 0, end = None, seed = Some(1337), questionId = QuestionId("question-id")),
        Timeout(3.seconds)
      ) { result =>
        result.questionTopIdeas.size should be(2)
        result.questionTopIdeas.head.ideaId should be(IdeaId("idea-1"))
        result.questionTopIdeas.head.proposalsCount should be(42)
        result.questionTopIdeas.head.avatars.size should be(4)
        result.questionTopIdeas.head.commentsCount should be(5)
      }
    }

    scenario("get top ideas if no proposals found") {
      when(
        topIdeaService
          .search(start = 0, end = None, ideaId = None, questionIds = Some(Seq(QuestionId("question-id"))), name = None)
      ).thenReturn(
        Future
          .successful(
            Seq(
              TopIdea(
                TopIdeaId("top-idea-1"),
                IdeaId("idea-1"),
                QuestionId("question-id"),
                "top-idea-1",
                "label",
                TopIdeaScores(0, 0, 0),
                42
              ),
              TopIdea(
                TopIdeaId("top-idea-2"),
                IdeaId("idea-2"),
                QuestionId("question-id"),
                "top-idea-2",
                "label",
                TopIdeaScores(0, 0, 0),
                21
              )
            )
          )
      )

      when(elasticsearchProposalAPI.getRandomProposalsByIdeaWithAvatar(Seq(IdeaId("idea-1"), IdeaId("idea-2")), 1337))
        .thenReturn(Future.successful(Map()))

      whenReady(
        questionService.getTopIdeas(start = 0, end = None, seed = Some(1337), questionId = QuestionId("question-id")),
        Timeout(3.seconds)
      ) { result =>
        result.questionTopIdeas.size should be(2)
        result.questionTopIdeas.head.ideaId should be(IdeaId("idea-1"))
        result.questionTopIdeas.head.proposalsCount should be(0)
        result.questionTopIdeas.head.avatars.size should be(0)
      }
    }
  }

  feature("get top idea by id") {
    scenario("top idea exists") {
      when(persistentTopIdeaService.getByIdAndQuestionId(TopIdeaId("top-idea-id"), QuestionId("question-id")))
        .thenReturn(
          Future.successful(
            Some(
              TopIdea(
                TopIdeaId("top-idea-id"),
                IdeaId("idea-id"),
                QuestionId("question-id"),
                name = "name",
                label = "label",
                TopIdeaScores(0, 0, 0),
                42
              )
            )
          )
        )

      when(elasticsearchProposalAPI.getRandomProposalsByIdeaWithAvatar(Seq(IdeaId("idea-id")), 1337))
        .thenReturn(
          Future.successful(
            Map(
              IdeaId("idea-id") ->
                AvatarsAndProposalsCount(
                  avatars = Seq("avatar-1", "avatar-2", "avatar-3", "avatar-4"),
                  proposalsCount = 42
                )
            )
          )
        )

      whenReady(
        questionService
          .getTopIdea(topIdeaId = TopIdeaId("top-idea-id"), questionId = QuestionId("question-id"), seed = Some(1337)),
        Timeout(3.seconds)
      ) { result =>
        result.isDefined should be(true)
        result.get.topIdea.name should be("name")
      }
    }

    scenario("top idea doesn't exist") {
      when(persistentTopIdeaService.getByIdAndQuestionId(TopIdeaId("not-found"), QuestionId("question-id")))
        .thenReturn(Future.successful(None))

      whenReady(
        questionService
          .getTopIdea(topIdeaId = TopIdeaId("not-found"), questionId = QuestionId("question-id"), seed = None),
        Timeout(3.seconds)
      ) { result =>
        result.isDefined should be(false)
      }
    }
  }

}
