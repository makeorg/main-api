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
import org.make.core.{DateHelper, RequestContext}
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.IndexedTag
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.ThemeId
import org.make.core.tag._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TagServiceComponent {
  def tagService: TagService
}

case class TagFilter(label: Option[String] = None,
                     tagTypeId: Option[TagTypeId] = None,
                     operationId: Option[OperationId] = None,
                     themeId: Option[ThemeId] = None,
                     country: Option[Country] = None,
                     language: Option[Language] = None)
object TagFilter {
  val empty: TagFilter = TagFilter()
}

trait TagService extends ShortenedNames {
  def getTag(slug: TagId): Future[Option[Tag]]
  def createLegacyTag(label: String): Future[Tag]
  def createTag(label: String,
                tagTypeId: TagTypeId,
                operationId: Option[OperationId],
                themeId: Option[ThemeId],
                country: Country,
                language: Language,
                display: TagDisplay = TagDisplay.Inherit,
                weight: Float = 0f): Future[Tag]
  def findAll(displayed: Boolean = false): Future[Seq[Tag]]
  def findAllDisplayed(): Future[Seq[Tag]]
  def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]]
  def findByOperationId(operationId: OperationId): Future[Seq[Tag]]
  def findByThemeId(themeId: ThemeId): Future[Seq[Tag]]
  def searchByLabel(partialLabel: String, like: Boolean): Future[Seq[Tag]]
  def updateTag(tagId: TagId,
                label: String,
                display: TagDisplay,
                tagTypeId: TagTypeId,
                weight: Float,
                operationId: Option[OperationId],
                themeId: Option[ThemeId],
                country: Country,
                language: Language,
                requestContext: RequestContext = RequestContext.empty): Future[Option[Tag]]
  def retrieveIndexedTags(tags: Seq[TagId]): Future[Option[Seq[IndexedTag]]]
  def search(start: Int,
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
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

  val tagService: TagService = new TagService {

    override def getTag(tagId: TagId): Future[Option[Tag]] = {
      persistentTagService.get(tagId)
    }

    override def findAll(displayed: Boolean): Future[Seq[Tag]] = {
      if (displayed) {
        findAllDisplayed()
      } else {
        persistentTagService.findAll()
      }
    }

    override def findAllDisplayed(): Future[Seq[Tag]] = {
      persistentTagService.findAllDisplayed()
    }

    override def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]] = {
      persistentTagService.findAllFromIds(tagIds)
    }

    override def findByOperationId(operationId: OperationId): Future[Seq[Tag]] = {
      persistentTagService.findByOperationId(operationId)
    }

    override def findByThemeId(themeId: ThemeId): Future[Seq[Tag]] = {
      persistentTagService.findByThemeId(themeId)
    }

    override def createLegacyTag(label: String): Future[Tag] = {
      val tag: Tag = Tag(
        tagId = idGenerator.nextTagId(),
        label = label,
        display = TagDisplay.Inherit,
        weight = 0f,
        tagTypeId = TagType.LEGACY.tagTypeId,
        operationId = None,
        themeId = None,
        country = Country("FR"),
        language = Language("fr")
      )
      persistentTagService.persist(tag)
    }

    override def createTag(label: String,
                           tagTypeId: TagTypeId,
                           operationId: Option[OperationId],
                           themeId: Option[ThemeId],
                           country: Country,
                           language: Language,
                           display: TagDisplay,
                           weight: Float): Future[Tag] = {
      val tag: Tag = Tag(
        tagId = idGenerator.nextTagId(),
        label = label,
        display = display,
        weight = weight,
        tagTypeId = tagTypeId,
        operationId = operationId,
        themeId = themeId,
        country = country,
        language = language
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
                    .exists(_ == TagDisplay.Displayed.shortName)
                )
              } else {
                IndexedTag(tagId = tag.tagId, label = tag.label, display = tag.display == TagDisplay.Displayed)
              }
            })
          }
      }
    }

    override def searchByLabel(partialLabel: String, like: Boolean): Future[Seq[Tag]] = {
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
                           operationId: Option[OperationId],
                           themeId: Option[ThemeId],
                           country: Country,
                           language: Language,
                           requestContext: RequestContext): Future[Option[Tag]] = {
      persistentTagService.get(tagId).flatMap {
        case Some(tag) =>
          for {
            tagType <- tagTypeService.getTagType(tagTypeId)
            updateTag <- persistentTagService.update(
              tag.copy(
                label = label,
                display =
                  if (tagType.exists(_.display.shortName == TagTypeDisplay.Hidden.shortName)) TagDisplay.Hidden
                  else display,
                tagTypeId = tagTypeId,
                weight = weight,
                operationId = operationId,
                themeId = themeId,
                country = country,
                language = language
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

    override def search(start: Int,
                        end: Option[Int],
                        sort: Option[String],
                        order: Option[String],
                        tagFilter: TagFilter): Future[Seq[Tag]] = {

      persistentTagService.search(
        start,
        end,
        sort,
        order,
        PersistentTagFilter(
          tagFilter.label,
          tagFilter.operationId,
          tagFilter.tagTypeId,
          tagFilter.themeId,
          tagFilter.country,
          tagFilter.language
        )
      )

    }

    override def count(tagFilter: TagFilter = TagFilter.empty): Future[Int] = {
      persistentTagService.count(
        PersistentTagFilter(
          tagFilter.label,
          tagFilter.operationId,
          tagFilter.tagTypeId,
          tagFilter.themeId,
          tagFilter.country,
          tagFilter.language
        )
      )
    }
  }
}
