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

package org.make.api.operation
import cats.data.OptionT
import cats.implicits._
import grizzled.slf4j.Logging
import org.make.api.operation.ModerationMode.Enrichment
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.question.{PersistentQuestionServiceComponent, QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.sequence.PersistentSequenceConfigurationComponent
import org.make.api.tag.{TagFilter, TagServiceComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation._
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.proposal.ProposalStatus
import org.make.core.question.{Question, QuestionId}
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  SequenceConfiguration,
  SequenceId,
  SpecificSequenceConfiguration
}
import org.make.core.technical.Pagination._
import org.make.core.{DateHelper, Order}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DefaultOperationOfQuestionServiceComponent extends OperationOfQuestionServiceComponent with Logging {
  this: PersistentQuestionServiceComponent
    with PersistentSequenceConfigurationComponent
    with PersistentOperationOfQuestionServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with ProposalSearchEngineComponent
    with IdGeneratorComponent
    with QuestionServiceComponent
    with OperationServiceComponent
    with TagServiceComponent =>

  override lazy val operationOfQuestionService: OperationOfQuestionService = new DefaultOperationOfQuestionService

  class DefaultOperationOfQuestionService extends OperationOfQuestionService {

    override def count(query: OperationOfQuestionSearchQuery): Future[Long] =
      elasticsearchOperationOfQuestionAPI.count(query)

    override def find(
      start: Start = Start.zero,
      end: Option[End] = None,
      sort: Option[String] = None,
      order: Option[Order] = None,
      request: SearchOperationsOfQuestions = SearchOperationsOfQuestions()
    ): Future[scala.Seq[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.search(
        start,
        end,
        sort,
        order,
        request.questionIds,
        request.operationIds,
        request.operationKind,
        request.openAt,
        request.endAfter,
        request.slug
      )
    }

    override def findByQuestionId(questionId: QuestionId): Future[Option[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.getById(questionId)
    }

    override def findByOperationId(operationId: OperationId): Future[Seq[OperationOfQuestion]] = {
      persistentOperationOfQuestionService.find(Some(operationId))
    }

    override def findByQuestionSlug(slug: String): Future[Option[OperationOfQuestion]] = {
      persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some(slug))).flatMap { results =>
        results.headOption.map { question =>
          findByQuestionId(question.questionId)
        }.getOrElse(Future.successful(None))
      }
    }

    override def search(searchQuery: OperationOfQuestionSearchQuery): Future[OperationOfQuestionSearchResult] = {
      elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(searchQuery)
    }

    override def updateWithQuestion(
      operationOfQuestion: OperationOfQuestion,
      question: Question
    ): Future[OperationOfQuestion] = {
      for {
        _       <- persistentQuestionService.modify(question)
        updated <- persistentOperationOfQuestionService.modify(operationOfQuestion)
        _       <- indexById(question.questionId)
      } yield updated
    }

    override def update(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
      for {
        result <- persistentOperationOfQuestionService.modify(operationOfQuestion)
        _      <- indexById(operationOfQuestion.questionId)
      } yield result
    }

    /**
      * Deletes an OperationOfQuestion and all its associated objects:
      * - The associated Question
      * - The associated sequence configuration
      *
      * @param questionId the operationOfQuestion to delete
      * @return a future to follow the completion
      */
    override def delete(questionId: QuestionId): Future[Unit] = {

      for {
        _ <- persistentOperationOfQuestionService.delete(questionId)
        _ <- persistentSequenceConfigurationService.delete(questionId)
        _ <- persistentQuestionService.delete(questionId)
      } yield {}

    }

    /**
      * This function will:
      * - Create a new question
      * - Create a new sequence for this question
      * - Create a new OperationOfQuestion
      *
      * @param parameters all needed parameters to create everything
      * @return the created OperationOfQuestion
      */
    override def create(parameters: CreateOperationOfQuestion): Future[OperationOfQuestion] = {
      val questionId = idGenerator.nextQuestionId()
      val sequenceId = idGenerator.nextSequenceId()

      val question = Question(
        questionId = questionId,
        slug = parameters.slug,
        countries = parameters.countries,
        language = parameters.language,
        question = parameters.question,
        shortTitle = parameters.shortTitle,
        operationId = Some(parameters.operationId)
      )

      val operationOfQuestion = OperationOfQuestion(
        questionId = questionId,
        operationId = parameters.operationId,
        startDate = parameters.startDate,
        endDate = parameters.endDate,
        operationTitle = parameters.operationTitle,
        landingSequenceId = sequenceId,
        canPropose = true,
        sequenceCardsConfiguration = SequenceCardsConfiguration.default,
        aboutUrl = None,
        metas = Metas(None, None, None),
        theme = QuestionTheme.default,
        description = OperationOfQuestion.defaultDescription,
        consultationImage = parameters.consultationImage,
        consultationImageAlt = parameters.consultationImageAlt,
        descriptionImage = parameters.descriptionImage,
        descriptionImageAlt = parameters.descriptionImageAlt,
        resultsLink = None,
        proposalsCount = 0,
        participantsCount = 0,
        actions = parameters.actions,
        featured = parameters.featured,
        votesCount = 0,
        votesTarget = 100_000,
        timeline = OperationOfQuestionTimeline(None, None, None),
        createdAt = DateHelper.now()
      )

      val sequenceConfiguration: SequenceConfiguration = createSequenceConfiguration(questionId, sequenceId)

      for {
        _         <- persistentQuestionService.persist(question)
        _         <- persistentSequenceConfigurationService.persist(sequenceConfiguration)
        persisted <- persistentOperationOfQuestionService.persist(operationOfQuestion)
        _         <- indexById(questionId)
      } yield persisted

    }

    private def createSequenceConfiguration(questionId: QuestionId, sequenceId: SequenceId) = {
      def specificSequenceConfigurationId() = idGenerator.nextSpecificSequenceConfigurationId()

      val mainSequence = ExplorationSequenceConfiguration.default(idGenerator.nextExplorationSequenceConfigurationId())
      val controversialSequence = SpecificSequenceConfiguration.otherSequenceDefault(specificSequenceConfigurationId())
      val popularSequence = SpecificSequenceConfiguration.otherSequenceDefault(specificSequenceConfigurationId())
      val keywordSequence = SpecificSequenceConfiguration.otherSequenceDefault(specificSequenceConfigurationId())

      val sequenceConfiguration =
        SequenceConfiguration.default.copy(
          sequenceId = sequenceId,
          questionId = questionId,
          mainSequence = mainSequence,
          controversial = controversialSequence,
          popular = popularSequence,
          keyword = keywordSequence
        )
      sequenceConfiguration
    }

    override def count(request: SearchOperationsOfQuestions): Future[Int] = {
      persistentOperationOfQuestionService.count(
        request.questionIds,
        request.operationIds,
        request.openAt,
        request.endAfter,
        request.slug
      )
    }

    override def indexById(questionId: QuestionId): Future[Option[IndexationStatus]] = {
      val immutableFields = elasticsearchOperationOfQuestionAPI
        .findOperationOfQuestionById(questionId)
        .map(_.map(_.immutableFields).getOrElse(IndexedOperationOfQuestion.ImmutableFields.empty))
      val futureIndexedOperationOfQuestion: Future[Option[IndexedOperationOfQuestion]] = (for {
        question            <- OptionT(questionService.getQuestion(questionId))
        operationOfQuestion <- OptionT(findByQuestionId(question.questionId))
        operation           <- OptionT(operationService.findOneSimple(operationOfQuestion.operationId))
      } yield IndexedOperationOfQuestion.createFromOperationOfQuestion(operationOfQuestion, operation, question)).value

      futureIndexedOperationOfQuestion.flatMap { ooq =>
        immutableFields.map(fields => ooq.map(_.applyImmutableFields(fields)))
      }.flatMap {
        case None => Future.successful(None)
        case Some(operationOfQuestion) =>
          elasticsearchOperationOfQuestionAPI.indexOperationOfQuestion(operationOfQuestion, None).map(Some(_))
      }

    }

    override def getQuestionsInfos(
      questionIds: Option[Seq[QuestionId]],
      moderationMode: ModerationMode,
      minVotesCount: Option[Int],
      minScore: Option[Double]
    ): Future[Seq[ModerationOperationOfQuestionInfosResponse]] = {
      elasticsearchOperationOfQuestionAPI
        .searchOperationOfQuestions(
          OperationOfQuestionSearchQuery(
            filters = Some(
              OperationOfQuestionSearchFilters(
                questionIds = questionIds.map(QuestionIdsSearchFilter),
                startDate = Some(StartDateSearchFilter(lte = Some(DateHelper.now()), gte = None)),
                endDate = Some(EndDateSearchFilter(lte = None, gte = Some(DateHelper.now().minusWeeks(2)))),
                operationKinds = Some(OperationKindsSearchFilter(OperationKind.values))
              )
            ),
            limit = questionIds.map(_.length + 1).orElse(Some(10000))
          )
        )
        .flatMap { questions =>
          val futureProposalToModerateByQuestion = elasticsearchProposalAPI.countProposalsByQuestion(
            maybeQuestionIds = Some(questions.results.map(_.questionId)),
            status =
              if (moderationMode == Enrichment) Some(Seq(ProposalStatus.Accepted))
              else Some(Seq(ProposalStatus.Pending)),
            maybeUserId = None,
            toEnrich = if (moderationMode == Enrichment) Some(true) else None,
            minVotesCount = if (moderationMode == Enrichment) minVotesCount else None,
            minScore = if (moderationMode == Enrichment) minScore else None
          )
          val futureProposalCountByQuestion = elasticsearchProposalAPI.countProposalsByQuestion(
            maybeQuestionIds = Some(questions.results.map(_.questionId)),
            status = Some(ProposalStatus.values),
            maybeUserId = None,
            toEnrich = None,
            minVotesCount = None,
            minScore = None
          )
          val futureHasTags = Future
            .traverse(questions.results) { q =>
              tagService.count(tagFilter = TagFilter(questionIds = Some(Seq(q.questionId)))).map(_ > 0).map { hasTags =>
                q.questionId -> hasTags
              }
            }
            .map(_.toMap)
          for {
            moderateCount <- futureProposalToModerateByQuestion
            totalCount    <- futureProposalCountByQuestion
            hasTags       <- futureHasTags
          } yield {
            questions.results.map { q =>
              ModerationOperationOfQuestionInfosResponse(
                q,
                moderateCount.getOrElse(q.questionId, 0L).toInt,
                totalCount.getOrElse(q.questionId, 0L).toInt,
                hasTags.getOrElse(q.questionId, false)
              )
            }.sortBy(_.proposalToModerateCount * -1)
          }
        }
    }

  }
}
