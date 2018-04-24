package org.make.api.tagtype

import org.make.api.technical.IdGeneratorComponent
import org.make.core.tag._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TagTypeServiceComponent {
  def tagTypeService: TagTypeService
}

trait TagTypeService {
  def getTagType(tagTypeId: TagTypeId): Future[Option[TagType]]
  def createTagType(label: String, display: TagTypeDisplay): Future[TagType]
  def findAll(): Future[Seq[TagType]]
  def findByTagTypeIds(tagIds: Seq[TagTypeId]): Future[Seq[TagType]]
  def updateTagType(tagTypeId: TagTypeId,
                    newTagTypeLabel: String,
                    newTagTypeDisplay: TagTypeDisplay): Future[Option[TagType]]
}

trait DefaultTagTypeServiceComponent extends TagTypeServiceComponent {
  this: PersistentTagTypeServiceComponent with IdGeneratorComponent =>

  val tagTypeService: TagTypeService = new TagTypeService {

    override def getTagType(slug: TagTypeId): Future[Option[TagType]] = {
      persistentTagTypeService.get(slug)
    }

    override def createTagType(label: String, display: TagTypeDisplay): Future[TagType] = {
      persistentTagTypeService.persist(
        TagType(tagTypeId = idGenerator.nextTagTypeId(), label = label, display = display)
      )
    }

    override def findAll(): Future[Seq[TagType]] = {
      persistentTagTypeService.findAll()
    }

    override def findByTagTypeIds(tagIds: Seq[TagTypeId]): Future[Seq[TagType]] = {
      persistentTagTypeService.findAllFromIds(tagIds)
    }

    override def updateTagType(tagTypeId: TagTypeId,
                               newTagTypeLabel: String,
                               newTagTypeDisplay: TagTypeDisplay): Future[Option[TagType]] = {
      getTagType(tagTypeId).flatMap {
        case Some(tagType) =>
          persistentTagTypeService
            .persist(tagType.copy(label = newTagTypeLabel, display = newTagTypeDisplay))
            .map(Some(_))
        case None => Future.successful(None)
      }
    }
  }
}
