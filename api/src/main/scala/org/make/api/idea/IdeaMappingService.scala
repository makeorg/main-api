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

package org.make.api.idea
import java.util.concurrent.Executors

import grizzled.slf4j.Logging
import org.make.api.proposal.{
  ModerationProposalResponse,
  PatchProposalRequest,
  ProposalSearchEngineComponent,
  ProposalServiceComponent
}
import org.make.api.question.PersistentQuestionServiceComponent
import org.make.api.tag.{PersistentTagServiceComponent, TagServiceComponent}
import org.make.api.tagtype.PersistentTagTypeServiceComponent
import org.make.api.technical.ExecutorServiceHelper._
import org.make.api.technical.IdGeneratorComponent
import org.make.core.idea._
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}
import org.make.core.proposal.{IdeaSearchFilter, SearchFilters, SearchQuery, TagsSearchFilter}
import org.make.core.question.{Question, QuestionId}
import org.make.core.tag.{Tag, TagId, TagTypeId}
import org.make.core.user.UserId
import org.make.core._

import scala.Ordering.Float.TotalOrdering
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination._
import org.postgresql.util.{PSQLException, PSQLState}

trait IdeaMappingService {
  def create(
    questionId: QuestionId,
    stakeTagId: Option[TagId],
    solutionTypeTagId: Option[TagId],
    ideaId: IdeaId
  ): Future[IdeaMapping]
  def getById(ideaMappingId: IdeaMappingId): Future[Option[IdeaMapping]]
  def changeIdea(
    adminId: UserId,
    IdeaMappingId: IdeaMappingId,
    newIdea: IdeaId,
    migrateProposals: Boolean
  ): Future[Option[IdeaMapping]]
  def search(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    questionId: Option[QuestionId],
    stakeTagId: Option[TagIdOrNone],
    solutionTypeTagId: Option[TagIdOrNone],
    ideaId: Option[IdeaId]
  ): Future[Seq[IdeaMapping]]
  def getOrCreateMapping(
    questionId: QuestionId,
    stakeTagId: Option[TagId],
    solutionTypeTagId: Option[TagId]
  ): Future[IdeaMapping]
  def count(
    questionId: Option[QuestionId],
    stakeTagId: Option[TagIdOrNone],
    solutionTypeTagId: Option[TagIdOrNone],
    ideaId: Option[IdeaId]
  ): Future[Int]
}

trait IdeaMappingServiceComponent {
  def ideaMappingService: IdeaMappingService
}

trait DefaultIdeaMappingServiceComponent extends IdeaMappingServiceComponent with Logging {
  self: PersistentIdeaMappingServiceComponent
    with PersistentIdeaServiceComponent
    with TagServiceComponent
    with ProposalServiceComponent
    with ProposalSearchEngineComponent
    with PersistentQuestionServiceComponent
    with PersistentTagServiceComponent
    with PersistentTagTypeServiceComponent
    with IdGeneratorComponent =>

  override val ideaMappingService: DefaultIdeaMappingService = new DefaultIdeaMappingService

  class DefaultIdeaMappingService extends IdeaMappingService {

    override def create(
      questionId: QuestionId,
      stakeTagId: Option[TagId],
      solutionTypeTagId: Option[TagId],
      ideaId: IdeaId
    ): Future[IdeaMapping] = {
      persistentIdeaMappingService.persist(
        IdeaMapping(idGenerator.nextIdeaMappingId(), questionId, stakeTagId, solutionTypeTagId, ideaId)
      )
    }

    override def getById(ideaMappingId: IdeaMappingId): Future[Option[IdeaMapping]] = {
      persistentIdeaMappingService.get(ideaMappingId)
    }

    override def changeIdea(
      adminId: UserId,
      ideaMappingId: IdeaMappingId,
      newIdea: IdeaId,
      migrateProposals: Boolean
    ): Future[Option[IdeaMapping]] = {
      persistentIdeaMappingService.get(ideaMappingId).flatMap {
        case None => Future.successful(None)
        case Some(mapping) =>
          persistentIdeaMappingService.updateMapping(mapping.copy(ideaId = newIdea)).flatMap { result =>
            if (migrateProposals) {
              updateProposalsIdea(adminId, newIdea, mapping).map(_ => result)
            } else {
              Future.successful(result)
            }
          }
      }

    }

    private def updateProposalsIdea(
      adminId: UserId,
      newIdea: IdeaId,
      mapping: IdeaMapping
    ): Future[Seq[ModerationProposalResponse]] = {

      val stakeLabel = "Stake"
      val solutionTypeLabel = "Solution type"

      val tagsFromMapping = Seq(mapping.stakeTagId, mapping.solutionTypeTagId).flatten

      val stakeAndSolutionTagTypeIds = persistentTagTypeService.findAll().map { tagTypes =>
        tagTypes
          .filter(tagType => tagType.label == stakeLabel || tagType.label == solutionTypeLabel)
          .map(tagType => tagType.label -> tagType.tagTypeId)
          .toMap
      }

      val searchQuery = SearchQuery(filters = Some(
        SearchFilters(
          idea = Some(IdeaSearchFilter(Seq(mapping.ideaId))),
          tags = Some(TagsSearchFilter(tagsFromMapping))
        )
      )
      )

      stakeAndSolutionTagTypeIds.flatMap { tagTypeMap =>
        elasticsearchProposalAPI
          .searchProposals(searchQuery)
          .flatMap { proposals =>
            val tagIdsFromProposals = proposals.results.flatMap(_.tags.map(_.tagId)).distinct
            persistentTagService
              .findAllFromIds(tagIdsFromProposals)
              .flatMap { tags =>
                val tagMap = tags.map(tag => tag.tagId -> tag).toMap
                val proposalsToMigrate =
                  proposalsFilter(stakeLabel, solutionTypeLabel, proposals, tagMap, mapping, tagTypeMap)
                Future
                  .traverse(proposalsToMigrate) { proposal =>
                    proposalService.patchProposal(
                      proposal.id,
                      adminId,
                      RequestContext.empty,
                      PatchProposalRequest(ideaId = Some(newIdea))
                    )
                  }(
                    bf = implicitly,
                    Executors
                      .newFixedThreadPool(1)
                      .instrument("updateProposalsIdea")
                      .toExecutionContext
                  )
                  .map(_.flatten)
              }
          }
      }
    }

    private def proposalsFilter(
      stakeLabel: String,
      solutionTypeLabel: String,
      proposals: ProposalsSearchResult,
      tagMap: Map[TagId, Tag],
      mapping: IdeaMapping,
      tagTypeMap: Map[String, TagTypeId]
    ): Seq[IndexedProposal] = {
      proposals.results.filter { proposal =>
        val proposalTags = proposal.tags.map(tag => tagMap(tag.tagId))
        val stakeTagTypeId: Option[TagTypeId] = tagTypeMap.get(stakeLabel)
        val heaviestStakeTag =
          proposalTags
            .filter(tag => stakeTagTypeId.contains(tag.tagTypeId))
            .sortBy(_.weight * -1)
            .headOption
            .map(_.tagId)
        val solutionTagTypeId: Option[TagTypeId] = tagTypeMap.get(solutionTypeLabel)
        val heaviestSolutionTag =
          proposalTags
            .filter(tag => solutionTagTypeId.contains(tag.tagTypeId))
            .sortBy(_.weight * -1)
            .headOption
            .map(_.tagId)
        mapping.stakeTagId == heaviestStakeTag && mapping.solutionTypeTagId == heaviestSolutionTag
      }
    }

    override def search(
      start: Start = Start.zero,
      end: Option[End] = None,
      sort: Option[String] = None,
      order: Option[Order] = None,
      questionId: Option[QuestionId],
      stakeTagId: Option[TagIdOrNone],
      solutionTypeTagId: Option[TagIdOrNone],
      ideaId: Option[IdeaId]
    ): Future[Seq[IdeaMapping]] = {
      persistentIdeaMappingService.find(start, end, sort, order, questionId, stakeTagId, solutionTypeTagId, ideaId)
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def getOrCreateMapping(
      questionId: QuestionId,
      stakeTagId: Option[TagId],
      solutionTypeTagId: Option[TagId]
    ): Future[IdeaMapping] = {

      persistentIdeaMappingService
        .find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          questionId = Some(questionId),
          stakeTagId = optionToTagIdOrNone(stakeTagId),
          solutionTypeTagId = optionToTagIdOrNone(solutionTypeTagId),
          ideaId = None
        )
        .flatMap {
          case Seq()        => createMapping(questionId, stakeTagId, solutionTypeTagId)
          case mapping +: _ => Future.successful(mapping)
        }
        .recoverWith {
          case e: PSQLException if e.getSQLState == PSQLState.UNIQUE_VIOLATION.getState =>
            logger.debug(
              s"Retried IdeaMappingService.getOrCreateMapping with questionId ${questionId.value}, stakeTagId: ${stakeTagId.toString}, solutionTypeTagId ${solutionTypeTagId.toString}"
            )
            getOrCreateMapping(questionId, stakeTagId, solutionTypeTagId)
          case other => Future.failed(other)
        }
    }

    private def optionToTagIdOrNone(maybeTagId: Option[TagId]): Option[TagIdOrNone] = {
      maybeTagId match {
        case Some(tagId) => Some(Right(tagId))
        case None        => Some(Left(None))
      }
    }
    private def createMapping(
      questionId: QuestionId,
      stakeTagId: Option[TagId],
      solutionTypeTagId: Option[TagId]
    ): Future[IdeaMapping] = {

      for {
        question <- retrieveQuestionOrFail(questionId)
        name     <- computeIdeaName(stakeTagId, solutionTypeTagId, question.slug)
        idea     <- createIdea(questionId, question, name)
        mapping <- persistentIdeaMappingService
          .persist(IdeaMapping(idGenerator.nextIdeaMappingId(), questionId, stakeTagId, solutionTypeTagId, idea.ideaId))
      } yield mapping
    }

    private def computeIdeaName(stake: Option[TagId], solution: Option[TagId], questionSlug: String): Future[String] = {
      val tags = Seq(stake, solution).flatten
      tagService
        .findByTagIds(tags)
        .map { tags =>
          val tagMap = tags.map(tag => Some(tag.tagId) -> tag.label).toMap[Option[TagId], String]
          s"${tagMap.getOrElse(stake, "None")} / ${tagMap.getOrElse(solution, s"None")} ($questionSlug)"
        }
    }

    private def retrieveQuestionOrFail(questionId: QuestionId): Future[Question] = {
      persistentQuestionService
        .getById(questionId)
        .flatMap {
          case None =>
            Future.failed(
              ValidationFailedError(
                Seq(
                  ValidationError("questionId", "invalid_content", Some(s"Question ${questionId.value} doesn't exist"))
                )
              )
            )
          case Some(question) =>
            Future.successful(question)
        }
    }

    private def createIdea(questionId: QuestionId, question: Question, label: String): Future[Idea] = {
      persistentIdeaService.persist(
        Idea(
          idGenerator.nextIdeaId(),
          label,
          Some(question.question),
          None,
          Some(questionId),
          IdeaStatus.Activated,
          Some(DateHelper.now()),
          Some(DateHelper.now())
        )
      )
    }

    override def count(
      questionId: Option[QuestionId],
      stakeTagId: Option[TagIdOrNone],
      solutionTypeTagId: Option[TagIdOrNone],
      ideaId: Option[IdeaId]
    ): Future[Int] = {
      persistentIdeaMappingService.count(questionId, stakeTagId, solutionTypeTagId, ideaId)
    }
  }
}
