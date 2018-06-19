package org.make.api.tag

import org.make.api.ActorSystemComponent
import org.make.api.proposal.ProposalCoordinatorServiceComponent
import org.make.api.sequence.SequenceCoordinatorServiceComponent
import org.make.api.tagtype.PersistentTagTypeServiceComponent
import org.make.api.technical._
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.IndexedTag
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
                     country: Option[String] = None,
                     language: Option[String] = None,
)
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
                country: String,
                language: String,
                display: TagDisplay = TagDisplay.Inherit,
                weight: Float = 0f): Future[Tag]
  def findAll(): Future[Seq[Tag]]
  def findAllDisplayed(): Future[Seq[Tag]]
  def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]]
  def findByOperationId(operationId: OperationId): Future[Seq[Tag]]
  def findByThemeId(themeId: ThemeId): Future[Seq[Tag]]
  def searchByLabel(partialLabel: String): Future[Seq[Tag]]
  def updateTag(tagId: TagId,
                label: String,
                display: TagDisplay,
                tagTypeId: TagTypeId,
                weight: Float,
                operationId: Option[OperationId],
                themeId: Option[ThemeId],
                country: String,
                language: String): Future[Option[Tag]]
  def retrieveIndexedTags(tags: Seq[TagId]): Future[Option[Seq[IndexedTag]]]
  def search(start: Int,
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
             tagFilter: TagFilter = TagFilter.empty): Future[Seq[Tag]]
}

trait DefaultTagServiceComponent
    extends TagServiceComponent
    with ShortenedNames
    with EventBusServiceComponent
    with ReadJournalComponent
    with ProposalCoordinatorServiceComponent
    with SequenceCoordinatorServiceComponent
    with ActorSystemComponent
    with PersistentTagTypeServiceComponent {
  this: PersistentTagServiceComponent with IdGeneratorComponent =>

  val tagService: TagService = new TagService {

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
        country = "FR",
        language = "fr"
      )
      persistentTagService.persist(tag)
    }

    override def createTag(label: String,
                           tagTypeId: TagTypeId,
                           operationId: Option[OperationId],
                           themeId: Option[ThemeId],
                           country: String,
                           language: String,
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
                  display = tagType.nonEmpty && tagType.head.display.shortName == TagDisplay.Displayed.shortName
                )
              } else {
                IndexedTag(tagId = tag.tagId, label = tag.label, display = tag.display == TagDisplay.Displayed)
              }
            })
          }
      }
    }

    override def searchByLabel(partialLabel: String): Future[Seq[Tag]] = {
      if (partialLabel.isEmpty) {
        persistentTagService.findAll()
      } else {
        persistentTagService.findByLabelLike(partialLabel)
      }
    }

    override def updateTag(tagId: TagId,
                           label: String,
                           display: TagDisplay,
                           tagTypeId: TagTypeId,
                           weight: Float,
                           operationId: Option[OperationId],
                           themeId: Option[ThemeId],
                           country: String,
                           language: String): Future[Option[Tag]] = {
      persistentTagService.get(tagId).flatMap {
        case Some(tag) =>
          persistentTagService
            .update(
              tag.copy(
                label = label,
                display = display,
                tagTypeId = tagTypeId,
                weight = weight,
                operationId = operationId,
                themeId = themeId,
                country = country,
                language = language
              )
            )
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
  }
}
