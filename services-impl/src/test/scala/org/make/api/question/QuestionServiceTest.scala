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

package org.make.api.question

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
import org.make.api.technical.IdGeneratorComponent
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{EmptyActorSystemComponent, MakeUnitTest, TestUtils}
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.personality.{Personality, PersonalityId, PersonalityRoleId}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.technical.IdGenerator
import org.make.core.user._
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult, ProposalsAndVotesCountsByQuestion}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start
import org.mockito.Mockito.clearInvocations

class QuestionServiceTest
    extends MakeUnitTest
    with DefaultQuestionServiceComponent
    with PersistentQuestionServiceComponent
    with EmptyActorSystemComponent
    with IdGeneratorComponent
    with QuestionPersonalityServiceComponent
    with UserServiceComponent
    with OrganisationSearchEngineComponent
    with TopIdeaServiceComponent
    with ProposalSearchEngineComponent
    with PersistentTopIdeaServiceComponent
    with PersistentTopIdeaCommentServiceComponent {

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
      personalityRoleId = PersonalityRoleId("candidate")
    ),
    Personality(
      personalityId = PersonalityId("personality-2"),
      userId = UserId("user-2"),
      questionId = QuestionId("question-id"),
      personalityRoleId = PersonalityRoleId("candidate")
    )
  )

  val user1: User = TestUtils.user(id = UserId("user-1"))
  val user2: User = TestUtils.user(id = UserId("user-2"))

  Feature("get questions from cache") {
    Scenario("init and refresh") {
      val cacheQuestionId = QuestionId("cache-question")
      when(persistentQuestionService.getById(cacheQuestionId))
        .thenReturn(Future.successful(Some(TestUtils.question(cacheQuestionId))))
      clearInvocations(persistentQuestionService)
      whenReady(questionService.getCachedQuestion(cacheQuestionId), Timeout(3.seconds)) { _ =>
        verify(persistentQuestionService).getById(cacheQuestionId)
      }
      clearInvocations(persistentQuestionService)
      whenReady(questionService.getCachedQuestion(cacheQuestionId), Timeout(3.seconds)) { _ =>
        verify(persistentQuestionService, never).getById(cacheQuestionId)
      }
      whenReady(questionService.getCachedQuestion(cacheQuestionId), Timeout(3.seconds)) { _ =>
        verify(persistentQuestionService, never).getById(cacheQuestionId)
      }
      whenReady(questionService.getCachedQuestion(cacheQuestionId), Timeout(3.seconds)) { _ =>
        verify(persistentQuestionService, never).getById(cacheQuestionId)
      }
    }
  }

  Feature("Get question personalities") {
    Scenario("Get question personalities") {
      when(
        questionPersonalityService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = Some(QuestionId("question-id")),
          personalityRoleId = None
        )
      ).thenReturn(Future.successful(personalities))
      when(userService.getPersonality(UserId("user-1"))).thenReturn(Future.successful(Some(user1)))
      when(userService.getPersonality(UserId("user-2"))).thenReturn(Future.successful(Some(user2)))

      whenReady(
        questionService.getQuestionPersonalities(
          start = Start.zero,
          end = None,
          questionId = QuestionId("question-id"),
          personalityRoleId = None
        ),
        Timeout(3.seconds)
      ) { questionPersonalities =>
        questionPersonalities.map(_.userId) should contain(UserId("user-1"))
        questionPersonalities.map(_.userId) should contain(UserId("user-2"))
      }
    }
  }

  Feature("get question partners") {
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
        Country("FR"),
        None,
        Seq(ProposalsAndVotesCountsByQuestion(QuestionId("question-id"), scoreQuestion, 0))
      )

    Scenario("sort partners by counts") {
      when(
        elasticsearchOrganisationAPI.searchOrganisations(
          eqTo(
            OrganisationSearchQuery(
              filters = Some(
                OrganisationSearchFilters(organisationIds =
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

  Feature("get top ideas") {
    Scenario("get top ideas") {
      when(
        topIdeaService
          .search(
            start = Start.zero,
            end = None,
            ideaId = None,
            questionIds = Some(Seq(QuestionId("question-id"))),
            name = None
          )
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
        questionService
          .getTopIdeas(start = Start.zero, end = None, seed = Some(1337), questionId = QuestionId("question-id")),
        Timeout(3.seconds)
      ) { result =>
        result.questionTopIdeas.size should be(2)
        result.questionTopIdeas.head.ideaId should be(IdeaId("idea-1"))
        result.questionTopIdeas.head.proposalsCount should be(42)
        result.questionTopIdeas.head.avatars.size should be(4)
        result.questionTopIdeas.head.commentsCount should be(5)
      }
    }

    Scenario("get top ideas if no proposals found") {
      when(
        topIdeaService
          .search(
            start = Start.zero,
            end = None,
            ideaId = None,
            questionIds = Some(Seq(QuestionId("question-id"))),
            name = None
          )
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
        questionService
          .getTopIdeas(start = Start.zero, end = None, seed = Some(1337), questionId = QuestionId("question-id")),
        Timeout(3.seconds)
      ) { result =>
        result.questionTopIdeas.size should be(2)
        result.questionTopIdeas.head.ideaId should be(IdeaId("idea-1"))
        result.questionTopIdeas.head.proposalsCount should be(0)
        result.questionTopIdeas.head.avatars.size should be(0)
      }
    }
  }

  Feature("get top idea by id") {
    Scenario("top idea exists") {
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

    Scenario("top idea exists without proposal") {
      when(
        persistentTopIdeaService.getByIdAndQuestionId(TopIdeaId("top-idea-id-no-proposal"), QuestionId("question-id"))
      ).thenReturn(
        Future.successful(
          Some(
            TopIdea(
              TopIdeaId("top-idea-id-no-proposal"),
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
        .thenReturn(Future.successful(Map.empty))

      whenReady(
        questionService
          .getTopIdea(
            topIdeaId = TopIdeaId("top-idea-id-no-proposal"),
            questionId = QuestionId("question-id"),
            seed = Some(1337)
          ),
        Timeout(3.seconds)
      ) { result =>
        result.isDefined should be(true)
        result.get.topIdea.name should be("name")
        result.get.proposalsCount shouldBe 0
      }
    }

    Scenario("top idea doesn't exist") {
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
