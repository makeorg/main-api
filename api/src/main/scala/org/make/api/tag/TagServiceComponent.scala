package org.make.api.tag

import org.make.api.tag.TagExceptions.TagAlreadyExistsException
import org.make.api.technical.ShortenedNames
import org.make.core.reference.{Tag, TagId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TagServiceComponent {
  def tagService: TagService
}

trait TagService extends ShortenedNames {
  def getTag(slug: TagId): Future[Option[Tag]]
  def getTag(slug: String): Future[Option[Tag]]
  def findEnabledByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]]
  def createTag(label: String): Future[Tag]
  def findAll(): Future[Seq[Tag]]
  def findAllEnabled(): Future[Seq[Tag]]
  def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]]
}

trait DefaultTagServiceComponent extends TagServiceComponent with ShortenedNames {
  this: PersistentTagServiceComponent =>

  val tagService = new TagService {

    override def getTag(slug: TagId): Future[Option[Tag]] = {
      persistentTagService.get(slug)
    }

    override def getTag(slug: String): Future[Option[Tag]] = {
      persistentTagService.get(TagId(slug))
    }

    override def createTag(label: String): Future[Tag] = {
      val tag: Tag = Tag(label)
      persistentTagService.get(tag.tagId).flatMap { result =>
        if (result.isDefined) {
          Future.failed(TagAlreadyExistsException(tag.label))
        } else {
          persistentTagService.persist(tag)
        }
      }
    }

    override def findEnabledByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]] = {
      persistentTagService.findAllEnabledFromIds(tagIds)
    }

    override def findAllEnabled(): Future[Seq[Tag]] = {
      persistentTagService.findAllEnabled()
    }

    override def findAll(): Future[Seq[Tag]] = {
      persistentTagService.findAll()
    }

    override def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]] = {
      findAll().map(_.filter(tag => tagIds.contains(tag.tagId)))
    }
  }
}
