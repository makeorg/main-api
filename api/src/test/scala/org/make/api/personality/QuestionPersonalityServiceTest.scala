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

package org.make.api.personality

import org.make.api.MakeUnitTest
import org.make.api.idea.topIdeaComments.{TopIdeaCommentService, TopIdeaCommentServiceComponent}
import org.make.api.idea.{TopIdeaService, TopIdeaServiceComponent}
import org.make.api.operation.{OperationOfQuestionService, OperationOfQuestionServiceComponent}
import org.make.api.proposal.{ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.question.{AvatarsAndProposalsCount, QuestionService, QuestionServiceComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.idea._
import org.make.core.operation._
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.personality.{Personality, PersonalityId, PersonalityRoleId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class QuestionPersonalityServiceTest
    extends MakeUnitTest
    with DefaultQuestionPersonalityServiceComponent
    with PersistentQuestionPersonalityServiceComponent
    with IdGeneratorComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with TopIdeaServiceComponent
    with TopIdeaCommentServiceComponent
    with ProposalSearchEngineComponent {

  override val persistentQuestionPersonalityService: PersistentQuestionPersonalityService =
    mock[PersistentQuestionPersonalityService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val topIdeaCommentService: TopIdeaCommentService = mock[TopIdeaCommentService]
  override val topIdeaService: TopIdeaService = mock[TopIdeaService]
  override val questionService: QuestionService = mock[QuestionService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]

  val personality: Personality = Personality(
    personalityId = PersonalityId("personality"),
    userId = UserId("user-id"),
    questionId = QuestionId("question"),
    personalityRoleId = PersonalityRoleId("candidate")
  )

  feature("create personality") {
    scenario("creation") {
      Mockito.when(idGenerator.nextPersonalityId()).thenReturn(PersonalityId("personality"))
      Mockito.when(persistentQuestionPersonalityService.persist(personality)).thenReturn(Future.successful(personality))

      whenReady(
        questionPersonalityService.createPersonality(request = CreateQuestionPersonalityRequest(
          userId = UserId("user-id"),
          questionId = QuestionId("question"),
          personalityRoleId = PersonalityRoleId("candidate")
        )
        ),
        Timeout(2.seconds)
      ) { personality =>
        personality.personalityId should be(PersonalityId("personality"))
      }
    }
  }

  feature("update personality") {
    scenario("update when no personality is found") {
      Mockito
        .when(persistentQuestionPersonalityService.getById(PersonalityId("not-found")))
        .thenReturn(Future.successful(None))

      whenReady(
        questionPersonalityService.updatePersonality(
          personalityId = PersonalityId("not-found"),
          UpdateQuestionPersonalityRequest(
            userId = UserId("user-id"),
            personalityRoleId = PersonalityRoleId("candidate")
          )
        ),
        Timeout(2.seconds)
      ) { personality =>
        personality should be(None)
      }
    }

    scenario("update when personality is found") {
      val updatedPersonality: Personality = personality.copy(userId = UserId("update-user"))

      Mockito
        .when(persistentQuestionPersonalityService.getById(PersonalityId("personality")))
        .thenReturn(Future.successful(Some(personality)))
      Mockito
        .when(persistentQuestionPersonalityService.modify(updatedPersonality))
        .thenReturn(Future.successful(updatedPersonality))

      whenReady(
        questionPersonalityService.updatePersonality(
          personalityId = PersonalityId("personality"),
          UpdateQuestionPersonalityRequest(
            userId = UserId("update-user"),
            personalityRoleId = PersonalityRoleId("candidate")
          )
        ),
        Timeout(2.seconds)
      ) { personality =>
        personality.map(_.userId.value) should be(Some("update-user"))
      }
    }
  }

  feature("personalities opinions by questions") {
    scenario("empty list of top ideas") {
      when(questionService.getQuestions(ArgumentMatchers.eq(Seq.empty))).thenReturn(Future.successful(Seq.empty))
      when(
        operationOfQuestionService.search(
          ArgumentMatchers.eq(
            OperationOfQuestionSearchQuery(filters =
              Some(OperationOfQuestionSearchFilters(questionIds = Some(QuestionIdsSearchFilter(Seq.empty))))
            )
          )
        )
      ).thenReturn(Future.successful(OperationOfQuestionSearchResult(total = 0L, results = Seq.empty)))
      when(
        topIdeaService.search(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(Seq.empty)),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(Future.successful(Seq.empty))
      when(
        topIdeaCommentService
          .search(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(Seq.empty)),
            ArgumentMatchers.eq(Some(Seq.empty))
          )
      ).thenReturn(Future.successful(Seq.empty))
      when(topIdeaCommentService.countForAll(ArgumentMatchers.eq(Seq.empty)))
        .thenReturn(Future.successful(Map.empty))
      when(
        elasticsearchProposalAPI
          .getRandomProposalsByIdeaWithAvatar(ArgumentMatchers.eq(Seq.empty), ArgumentMatchers.any[Int])
      ).thenReturn(Future.successful(Map.empty))

      whenReady(questionPersonalityService.getPersonalitiesOpinionsByQuestions(Seq.empty), Timeout(2.seconds)) {
        opinions =>
          opinions shouldBe empty
      }
    }

    scenario("all comments") {
      when(
        questionPersonalityService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(UserId("personality-id"))),
          ArgumentMatchers.any[Option[QuestionId]],
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            Personality(
              PersonalityId("one"),
              UserId("personality-id"),
              QuestionId("question-id-one"),
              personalityRoleId = PersonalityRoleId("candidate")
            ),
            Personality(
              PersonalityId("two"),
              UserId("personality-id"),
              QuestionId("question-id-two"),
              personalityRoleId = PersonalityRoleId("candidate")
            )
          )
        )
      )
      when(
        questionService
          .getQuestions(ArgumentMatchers.eq(Seq(QuestionId("question-id-one"), QuestionId("question-id-two"))))
      ).thenReturn(
        Future.successful(
          Seq(
            Question(QuestionId("question-id-one"), "slug", Country("FR"), Language("fr"), "question", None, None),
            Question(QuestionId("question-id-two"), "slug", Country("FR"), Language("fr"), "question", None, None)
          )
        )
      )
      when(
        operationOfQuestionService.search(
          ArgumentMatchers.eq(
            OperationOfQuestionSearchQuery(filters = Some(
              OperationOfQuestionSearchFilters(questionIds =
                Some(QuestionIdsSearchFilter(Seq(QuestionId("question-id-one"), QuestionId("question-id-two"))))
              )
            )
            )
          )
        )
      ).thenReturn(
        Future.successful(
          OperationOfQuestionSearchResult(
            total = 2L,
            results = Seq(
              IndexedOperationOfQuestion(
                operationId = OperationId("operation-id-one"),
                questionId = QuestionId("question-id-one"),
                startDate = None,
                endDate = None,
                operationTitle = "title",
                question = "",
                slug = "",
                description = "Description opeOfQue",
                theme = QuestionTheme("#000000", "#000000", "#000000", "#000000", None, None),
                consultationImage = None,
                country = Country("FR"),
                language = Language("fr"),
                operationKind = "",
                aboutUrl = Some("http://about")
              ),
              IndexedOperationOfQuestion(
                operationId = OperationId("operation-id-two"),
                questionId = QuestionId("question-id-two"),
                startDate = None,
                endDate = None,
                operationTitle = "title",
                question = "",
                slug = "",
                description = "Description opeOfQue",
                theme = QuestionTheme("#000000", "#000000", "#000000", "#000000", Some("#000000"), Some("#000000")),
                consultationImage = None,
                country = Country("FR"),
                language = Language("fr"),
                operationKind = "",
                aboutUrl = Some("http://about")
              )
            )
          )
        )
      )
      when(
        topIdeaService.search(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(Seq(QuestionId("question-id-one"), QuestionId("question-id-two")))),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(
            TopIdea(
              TopIdeaId("top-idea-id"),
              IdeaId("idea-id"),
              QuestionId("question-id-one"),
              "name",
              "label",
              TopIdeaScores(0f, 0f, 0f),
              0f
            ),
            TopIdea(
              TopIdeaId("top-idea-id-2"),
              IdeaId("idea-id-2"),
              QuestionId("question-id-two"),
              "name",
              "label",
              TopIdeaScores(0f, 0f, 0f),
              0f
            )
          )
        )
      )
      when(
        topIdeaCommentService
          .search(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(Seq(TopIdeaId("top-idea-id"), TopIdeaId("top-idea-id-2")))),
            ArgumentMatchers.eq(Some(Seq(UserId("personality-id"))))
          )
      ).thenReturn(
        Future.successful(
          Seq(
            TopIdeaComment(
              TopIdeaCommentId("top-idea-comment-id"),
              TopIdeaId("top-idea-id"),
              UserId("personality-id"),
              Some("comment one"),
              Some("comment two"),
              None,
              CommentVoteKey.Agree,
              None
            )
          )
        )
      )
      when(
        topIdeaCommentService
          .countForAll(ArgumentMatchers.eq(Seq(TopIdeaId("top-idea-id"), TopIdeaId("top-idea-id-2"))))
      ).thenReturn(Future.successful(Map("top-idea-id" -> 2, "top-idea-id-2" -> 0)))
      when(
        elasticsearchProposalAPI
          .getRandomProposalsByIdeaWithAvatar(
            ArgumentMatchers.eq(Seq(IdeaId("idea-id"), IdeaId("idea-id-2"))),
            ArgumentMatchers.any[Int]
          )
      ).thenReturn(
        Future.successful(
          Map(
            IdeaId("idea-id-2") -> AvatarsAndProposalsCount(Seq("http://example.com/42", "http://example.com/84"), 21)
          )
        )
      )

      val personalities =
        Seq(
          Personality(
            PersonalityId("one"),
            UserId("personality-id"),
            QuestionId("question-id-one"),
            personalityRoleId = PersonalityRoleId("candidate")
          ),
          Personality(
            PersonalityId("two"),
            UserId("personality-id"),
            QuestionId("question-id-two"),
            personalityRoleId = PersonalityRoleId("candidate")
          )
        )

      whenReady(questionPersonalityService.getPersonalitiesOpinionsByQuestions(personalities), Timeout(2.seconds)) {
        opinions =>
          opinions.size shouldBe 2
          opinions.head.comment shouldBe defined
          opinions(1).comment shouldBe empty
          opinions.head.topIdea.avatars shouldBe empty
          opinions(1).topIdea.avatars.size shouldBe 2
      }
    }

  }
}
