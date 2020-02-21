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

package org.make.api.tag

import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.proposal.PublishedProposalEvent.ReindexProposal
import org.make.api.tagtype.{PersistentTagTypeServiceComponent, TagTypeServiceComponent}
import org.make.api.technical._
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedTag
import org.make.core.question.{Question, QuestionId}
import org.make.core.tag._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TagServiceComponent {
  def tagService: TagService
}

case class TagFilter(label: Option[String] = None,
                     tagTypeId: Option[TagTypeId] = None,
                     questionId: Option[QuestionId] = None)

object TagFilter {
  val empty: TagFilter = TagFilter()
}

trait TagService extends ShortenedNames {
  def getTag(slug: TagId): Future[Option[Tag]]
  def createTag(label: String,
                tagTypeId: TagTypeId,
                question: Question,
                display: TagDisplay = TagDisplay.Inherit,
                weight: Float = 0f): Future[Tag]
  def findAll(): Future[Seq[Tag]]
  def findAllDisplayed(): Future[Seq[Tag]]
  def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]]
  def findByQuestionId(questionId: QuestionId): Future[Seq[Tag]]
  def findByQuestionIds(questionIds: Seq[QuestionId]): Future[Map[QuestionId, Seq[TagId]]]
  def findByLabel(partialLabel: String, like: Boolean): Future[Seq[Tag]]
  def updateTag(tagId: TagId,
                label: String,
                display: TagDisplay,
                tagTypeId: TagTypeId,
                weight: Float,
                question: Question,
                requestContext: RequestContext = RequestContext.empty): Future[Option[Tag]]
  def retrieveIndexedTags(tags: Seq[TagId]): Future[Option[Seq[IndexedTag]]]
  def retrieveIndexedStakeTags(tags: Seq[TagId]): Future[Seq[IndexedTag]]
  def find(start: Int = 0,
           end: Option[Int] = None,
           sort: Option[String] = None,
           order: Option[String] = None,
           onlyDisplayed: Boolean = false,
           tagFilter: TagFilter = TagFilter.empty): Future[Seq[Tag]]
  def count(tagFilter: TagFilter = TagFilter.empty): Future[Int]
}

trait DefaultTagServiceComponent
    extends TagServiceComponent
    with ShortenedNames
    with ProposalSearchEngineComponent
    with TagTypeServiceComponent
    with PersistentTagTypeServiceComponent {
  this: PersistentTagServiceComponent with EventBusServiceComponent with IdGeneratorComponent =>

  override lazy val tagService: TagService = new DefaultTagService

  class DefaultTagService extends TagService {

    override def getTag(tagId: TagId): Future[Option[Tag]] = {
      persistentTagService.get(tagId)
    }

    override def findAll(): Future[Seq[Tag]] = {
      persistentTagService.findAll()
    }

    override def findAllDisplayed(): Future[Seq[Tag]] = {
      persistentTagService.findAllDisplayed()
    }

    override def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]] = {
      persistentTagService.findAllFromIds(tagIds)
    }

    override def findByQuestionId(questionId: QuestionId): Future[Seq[Tag]] = {
      persistentTagService.findByQuestion(questionId)
    }

    override def createTag(label: String,
                           tagTypeId: TagTypeId,
                           question: Question,
                           display: TagDisplay,
                           weight: Float): Future[Tag] = {
      val tag: Tag = Tag(
        tagId = idGenerator.nextTagId(),
        label = label,
        display = display,
        weight = weight,
        tagTypeId = tagTypeId,
        operationId = question.operationId,
        questionId = Some(question.questionId),
        country = question.country,
        language = question.language
      )
      persistentTagService.persist(tag)
    }

    override def retrieveIndexedTags(tags: Seq[TagId]): Future[Option[Seq[IndexedTag]]] = {
      val tagTypes: Future[Seq[TagType]] = persistentTagTypeService.findAll()

      tagTypes.flatMap { tagTypes =>
        tagService
          .findByTagIds(tags)
          .map { tags =>
            Some(tags.map { tag =>
              if (tag.display == TagDisplay.Inherit) {
                val tagType: Seq[TagType] = tagTypes.filter(tagType => tagType.tagTypeId == tag.tagTypeId)
                IndexedTag(
                  tagId = tag.tagId,
                  label = tag.label,
                  display = tagType.nonEmpty && tagType.headOption
                    .map(_.display.shortName)
                    .contains(TagDisplay.Displayed.shortName)
                )
              } else {
                IndexedTag(tagId = tag.tagId, label = tag.label, display = tag.display == TagDisplay.Displayed)
              }
            })
          }
      }
    }

    override def retrieveIndexedStakeTags(tags: Seq[TagId]): Future[Seq[IndexedTag]] = {
      val stake: Future[Option[TagType]] =
        persistentTagTypeService.findAll().map(_.find(_.label.toLowerCase == "stake"))

      stake.flatMap {
        case Some(stake) => Future.successful(stake)
        case None        => Future.failed(new IllegalStateException("Unable to find stake tag types"))
      }.flatMap { stake =>
        tagService
          .findByTagIds(tags)
          .map { tags =>
            tags.filter(_.tagTypeId.value == stake.tagTypeId.value).map { tag =>
              if (tag.display == TagDisplay.Inherit) {
                IndexedTag(
                  tagId = tag.tagId,
                  label = tag.label,
                  display = stake.display.shortName
                    .contains(TagDisplay.Displayed.shortName)
                )
              } else {
                IndexedTag(tagId = tag.tagId, label = tag.label, display = tag.display == TagDisplay.Displayed)
              }
            }
          }
      }
    }

    override def findByLabel(partialLabel: String, like: Boolean): Future[Seq[Tag]] = {
      if (partialLabel.isEmpty) {
        persistentTagService.findAll()
      } else if (like) {
        persistentTagService.findByLabelLike(partialLabel)
      } else {
        persistentTagService.findByLabel(partialLabel)
      }
    }

    override def updateTag(tagId: TagId,
                           label: String,
                           display: TagDisplay,
                           tagTypeId: TagTypeId,
                           weight: Float,
                           question: Question,
                           requestContext: RequestContext): Future[Option[Tag]] = {
      persistentTagService.get(tagId).flatMap {
        case Some(tag) =>
          for {
            tagType <- tagTypeService.getTagType(tagTypeId)
            updateTag <- persistentTagService.update(
              tag.copy(
                label = label,
                display = if (tagType.exists(_.display.shortName == TagTypeDisplay.Hidden.shortName)) {
                  TagDisplay.Hidden
                } else {
                  display
                },
                tagTypeId = tagTypeId,
                weight = weight,
                operationId = question.operationId,
                questionId = Some(question.questionId),
                country = question.country,
                language = question.language
              )
            )
            _ <- elasticsearchProposalAPI
              .searchProposals(SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(tagId)))))))
              .map(_.results.foreach { proposal =>
                eventBusService.publish(ReindexProposal(proposal.id, DateHelper.now(), requestContext))
              })
          } yield updateTag
        case None => Future.successful(None)
      }
    }

    override def find(start: Int = 0,
                      end: Option[Int] = None,
                      sort: Option[String] = None,
                      order: Option[String] = None,
                      onlyDisplayed: Boolean = false,
                      tagFilter: TagFilter): Future[Seq[Tag]] = {

      persistentTagService.find(
        start,
        end,
        sort,
        order,
        onlyDisplayed,
        PersistentTagFilter(tagFilter.label, tagFilter.questionId, tagFilter.tagTypeId)
      )

    }

    override def count(tagFilter: TagFilter = TagFilter.empty): Future[Int] = {
      persistentTagService.count(PersistentTagFilter(tagFilter.label, tagFilter.questionId, tagFilter.tagTypeId))
    }
    override def findByQuestionIds(questionIds: Seq[QuestionId]): Future[Map[QuestionId, Seq[TagId]]] = {
      persistentTagService.findByQuestions(questionIds).map(_.groupBy(_.questionId)).map {
        _.flatMap {
          case (Some(questionId), tags) => Some(questionId -> tags.map(_.tagId))
          case _                        => None
        }
      }
    }
  }
}
