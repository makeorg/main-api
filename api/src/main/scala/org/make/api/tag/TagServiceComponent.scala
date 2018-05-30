package org.make.api.tag

import org.make.api.ActorSystemComponent
import org.make.api.proposal.ProposalCoordinatorServiceComponent
import org.make.api.sequence.SequenceCoordinatorServiceComponent
import org.make.api.technical._
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TagServiceComponent {
  def tagService: TagService
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
}

trait DefaultTagServiceComponent
    extends TagServiceComponent
    with ShortenedNames
    with EventBusServiceComponent
    with ReadJournalComponent
    with ProposalCoordinatorServiceComponent
    with SequenceCoordinatorServiceComponent
    with ActorSystemComponent {
  this: PersistentTagServiceComponent with IdGeneratorComponent =>

  val tagService: TagService = new TagService {

    override def getTag(tagId: TagId): Future[Option[Tag]] = {
      persistentTagService.get(tagId)
    }

    override def createLegacyTag(label: String): Future[Tag] = {
//      TEMPORARY TAG CREATION. TagType "Legacy" with no link and FR_fr
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

    override def findAll(): Future[Seq[Tag]] = {
      persistentTagService.findAll()
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

  }
}
