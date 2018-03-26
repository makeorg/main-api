package org.make.api.tag

import akka.Done
import akka.stream.ActorMaterializer
import cats.data.OptionT
import cats.implicits._
import org.make.api.ActorSystemComponent
import org.make.api.proposal.ProposalCoordinatorServiceComponent
import org.make.api.sequence.SequenceCoordinatorServiceComponent
import org.make.api.tag.TagExceptions.TagAlreadyExistsException
import org.make.api.technical.{EventBusServiceComponent, ReadJournalComponent, ShortenedNames}
import org.make.api.userhistory.UserEvent.UserUpdatedTagEvent
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.reference.{Tag, TagId}
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId

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
  def updateTag(slug: TagId,
                newTagLabel: String,
                connectedUserId: Option[UserId] = None,
                requestContext: RequestContext = RequestContext.empty): Future[Option[Tag]]
}

trait DefaultTagServiceComponent
    extends TagServiceComponent
    with ShortenedNames
    with EventBusServiceComponent
    with ReadJournalComponent
    with ProposalCoordinatorServiceComponent
    with SequenceCoordinatorServiceComponent
    with ActorSystemComponent {
  this: PersistentTagServiceComponent =>

  val tagService: TagService = new TagService {

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

    override def updateTag(slug: TagId,
                           newTagLabel: String,
                           connectedUserId: Option[UserId] = None,
                           requestContext: RequestContext = RequestContext.empty): Future[Option[Tag]] = {

      eventBusService.publish(
        UserUpdatedTagEvent(
          connectedUserId = connectedUserId,
          requestContext = requestContext,
          oldTag = slug.value,
          newTag = newTagLabel
        )
      )

      val newTagToCreate: Tag = Tag(newTagLabel)
      val newTag: OptionT[Future, Tag] = for {
        oldTag <- OptionT(getTag(slug))
        newTag <- OptionT(persistentTagService.persist(newTagToCreate).map(Option(_)))
        _      <- OptionT(updateProposalTag(oldTag.tagId, newTag.tagId).map(Option(_)))
        _      <- OptionT(updateSequenceTag(oldTag.tagId, newTag.tagId).map(Option(_)))
        rows   <- OptionT(persistentTagService.remove(oldTag.tagId).map(Option(_)))
        if rows >= 1
      } yield newTag

      newTag.value
    }

    private def updateProposalTag(oldTag: TagId, newTag: TagId): Future[Done] = {
      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
      readJournal
        .currentPersistenceIds()
        .map { id =>
          proposalCoordinatorService.updateProposalTag(ProposalId(id), oldTag, newTag)
          Done
        }
        .runForeach { _ =>
          {}
        }
    }

    private def updateSequenceTag(oldTag: TagId, newTag: TagId): Future[Done] = {
      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
      readJournal
        .currentPersistenceIds()
        .map { id =>
          sequenceCoordinatorService.updateSequenceTag(SequenceId(id), oldTag, newTag)
          Done
        }
        .runForeach { _ =>
          {}
        }
    }

  }
}
