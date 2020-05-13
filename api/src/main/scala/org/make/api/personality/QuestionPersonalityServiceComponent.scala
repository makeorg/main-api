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

import org.make.api.idea.TopIdeaServiceComponent
import org.make.api.idea.topIdeaComments.TopIdeaCommentServiceComponent
import org.make.api.operation.OperationOfQuestionServiceComponent
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.question._
import org.make.api.technical.{IdGeneratorComponent, MakeRandom, ShortenedNames}
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaComment}
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.operation.{
  OperationOfQuestionSearchFilters,
  OperationOfQuestionSearchQuery,
  QuestionIdsSearchFilter
}
import org.make.core.personality.{Personality, PersonalityId, PersonalityRoleId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait QuestionPersonalityServiceComponent {
  def questionPersonalityService: QuestionPersonalityService
}

trait QuestionPersonalityService extends ShortenedNames {
  def getPersonality(personalityId: PersonalityId): Future[Option[Personality]]
  def find(
    start: Int,
    end: Option[Int],
    sort: Option[String],
    order: Option[String],
    userId: Option[UserId],
    questionId: Option[QuestionId],
    personalityRoleId: Option[PersonalityRoleId]
  ): Future[Seq[Personality]]
  def count(
    userId: Option[UserId],
    questionId: Option[QuestionId],
    personalityRoleId: Option[PersonalityRoleId]
  ): Future[Int]
  def createPersonality(request: CreateQuestionPersonalityRequest): Future[Personality]
  def updatePersonality(
    personalityId: PersonalityId,
    request: UpdateQuestionPersonalityRequest
  ): Future[Option[Personality]]
  def deletePersonality(personalityId: PersonalityId): Future[Unit]
  def getPersonalitiesOpinionsByQuestions(personalities: Seq[Personality]): Future[Seq[PersonalityOpinionResponse]]
}

trait DefaultQuestionPersonalityServiceComponent extends QuestionPersonalityServiceComponent {
  this: PersistentQuestionPersonalityServiceComponent
    with IdGeneratorComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with TopIdeaServiceComponent
    with TopIdeaCommentServiceComponent
    with ProposalSearchEngineComponent =>

  override lazy val questionPersonalityService: DefaultQuestionPersonalityService =
    new DefaultQuestionPersonalityService

  class DefaultQuestionPersonalityService extends QuestionPersonalityService {

    override def getPersonality(personalityId: PersonalityId): Future[Option[Personality]] = {
      persistentQuestionPersonalityService.getById(personalityId)
    }

    override def createPersonality(request: CreateQuestionPersonalityRequest): Future[Personality] = {
      val personality: Personality = Personality(
        personalityId = idGenerator.nextPersonalityId(),
        userId = request.userId,
        questionId = request.questionId,
        personalityRoleId = request.personalityRoleId
      )
      persistentQuestionPersonalityService.persist(personality)
    }

    override def updatePersonality(
      personalityId: PersonalityId,
      request: UpdateQuestionPersonalityRequest
    ): Future[Option[Personality]] = {
      persistentQuestionPersonalityService.getById(personalityId).flatMap {
        case Some(personality) =>
          persistentQuestionPersonalityService
            .modify(personality.copy(userId = request.userId, personalityRoleId = request.personalityRoleId))
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def find(
      start: Int,
      end: Option[Int],
      sort: Option[String],
      order: Option[String],
      userId: Option[UserId],
      questionId: Option[QuestionId],
      personalityRoleId: Option[PersonalityRoleId]
    ): Future[Seq[Personality]] = {
      persistentQuestionPersonalityService.find(start, end, sort, order, userId, questionId, personalityRoleId)
    }

    override def count(
      userId: Option[UserId],
      questionId: Option[QuestionId],
      personalityRoleId: Option[PersonalityRoleId]
    ): Future[Int] = {
      persistentQuestionPersonalityService.count(userId, questionId, personalityRoleId)
    }

    override def deletePersonality(personalityId: PersonalityId): Future[Unit] = {
      persistentQuestionPersonalityService.delete(personalityId)
    }

    private def opinionsResponse(
      topIdeas: Seq[TopIdea],
      questions: Seq[Question],
      opOfQuestions: Seq[IndexedOperationOfQuestion],
      topIdeaComments: Seq[TopIdeaComment],
      commentByTopIdea: Map[String, Int],
      avatarsAndProposalsCountByIdea: Map[IdeaId, AvatarsAndProposalsCount]
    ): Seq[PersonalityOpinionResponse] = {
      topIdeas.flatMap { topIdea =>
        val maybeQuestion = questions.find(_.questionId == topIdea.questionId)
        val maybeOpOfQuestion = opOfQuestions.find(_.questionId == topIdea.questionId)
        (maybeQuestion, maybeOpOfQuestion) match {
          case (Some(question), Some(opOfQuestion)) =>
            val simpleQuestion = SimpleQuestionResponse(
              question.questionId,
              question.slug,
              SimpleQuestionWordingResponse(opOfQuestion.operationTitle, opOfQuestion.question),
              opOfQuestion.country,
              opOfQuestion.language,
              opOfQuestion.startDate,
              opOfQuestion.endDate
            )
            val ideaAvatarsCount = avatarsAndProposalsCountByIdea
              .getOrElse(topIdea.ideaId, AvatarsAndProposalsCount(Seq.empty, 0))
            val topIdeaResponse = QuestionTopIdeaWithAvatarResponse(
              id = topIdea.topIdeaId,
              ideaId = topIdea.ideaId,
              questionId = topIdea.questionId,
              name = topIdea.name,
              label = topIdea.label,
              scores = topIdea.scores,
              proposalsCount = ideaAvatarsCount.proposalsCount,
              avatars = ideaAvatarsCount.avatars,
              weight = topIdea.weight,
              commentsCount = commentByTopIdea.getOrElse(topIdea.topIdeaId.value, 0)
            )

            Some(
              PersonalityOpinionResponse(
                simpleQuestion,
                topIdeaResponse,
                topIdeaComments
                  .find(_.topIdeaId == topIdea.topIdeaId)
                  .map(TopIdeaCommentResponse.apply)
              )
            )
          case _ => None
        }
      }
    }

    override def getPersonalitiesOpinionsByQuestions(
      personalities: Seq[Personality]
    ): Future[Seq[PersonalityOpinionResponse]] = {
      for {
        questions <- questionService.getQuestions(personalities.map(_.questionId))
        questionIds = questions.map(_.questionId)
        queryFilters = OperationOfQuestionSearchFilters(questionIds = Some(QuestionIdsSearchFilter(questionIds)))
        opOfQuestionsResult <- operationOfQuestionService.search(OperationOfQuestionSearchQuery(Some(queryFilters)))
        topIdeas            <- topIdeaService.search(0, None, None, None, None, Some(questionIds), None)
        topIdeaComments <- topIdeaCommentService.search(
          0,
          None,
          Some(topIdeas.map(_.topIdeaId)),
          Some(personalities.map(_.userId).distinct)
        )
        commentByTopIdea <- topIdeaCommentService.countForAll(topIdeas.map(_.topIdeaId))
        avatarsAndProposalsCountByIdea <- elasticsearchProposalAPI.getRandomProposalsByIdeaWithAvatar(
          ideaIds = topIdeas.map(_.ideaId),
          MakeRandom.nextInt()
        )
      } yield opinionsResponse(
        topIdeas,
        questions,
        opOfQuestionsResult.results,
        topIdeaComments,
        commentByTopIdea,
        avatarsAndProposalsCountByIdea
      )

    }

  }
}
