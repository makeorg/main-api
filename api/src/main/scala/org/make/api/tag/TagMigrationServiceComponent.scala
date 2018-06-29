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

import java.util.concurrent.Executors

import akka.Done
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.proposal.{
  PatchProposalCommand,
  PatchProposalRequest,
  ProposalCoordinatorComponent,
  ProposalCoordinatorServiceComponent
}
import org.make.api.technical._
import org.make.api.theme.ThemeServiceComponent
import org.make.core.RequestContext
import org.make.core.operation.{Operation, OperationId}
import org.make.core.proposal.{Proposal, ProposalId, ProposalStatus}
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.tag._
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

sealed trait JoinTagIds {
  val tagId: TagId
  val slug: TagId
}

final case class OperationJoinTagIds(operationId: OperationId, tagId: TagId, slug: TagId) extends JoinTagIds
final case class ThemeJoinTagIds(themeId: ThemeId, tagId: TagId, slug: TagId) extends JoinTagIds

trait TagMigrationServiceComponent {
  def tagMigrationService: TagMigrationService
}

trait TagMigrationService extends ShortenedNames {
  def createTagsByOperation: Future[Seq[OperationJoinTagIds]]
  def createTagsByTheme: Future[Seq[ThemeJoinTagIds]]
  def migrateTags(tagsByTheme: Seq[ThemeJoinTagIds],
                  tagsByOperation: Seq[OperationJoinTagIds],
                  author: UserId,
                  requestContext: RequestContext): Future[Done]
  def createPatchProposalCommand(proposal: Proposal,
                                 tagsByTheme: Seq[ThemeJoinTagIds],
                                 tagsByOperation: Seq[OperationJoinTagIds],
                                 author: UserId,
                                 requestContext: RequestContext): PatchProposalCommand
}

trait DefaultTagMigrationServiceComponent
    extends TagMigrationServiceComponent
    with ActorSystemComponent
    with StrictLogging {
  this: TagServiceComponent
    with OperationServiceComponent
    with ThemeServiceComponent
    with ProposalCoordinatorServiceComponent
    with ProposalCoordinatorComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  val tagMigrationService: TagMigrationService = new TagMigrationService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout.duration * 2

    override def createTagsByTheme: Future[Seq[ThemeJoinTagIds]] = {
      themeService.findAll().flatMap { themes =>
        createNewTagsForTheme(themes)
      }
    }

    override def createTagsByOperation: Future[Seq[OperationJoinTagIds]] = {
      tagService.findAll().flatMap { oldTags =>
        operationService.find().flatMap { operations =>
          createNewTagsForOperation(oldTags, operations)
        }
      }
    }

    override def migrateTags(tagsByTheme: Seq[ThemeJoinTagIds],
                             tagsByOperation: Seq[OperationJoinTagIds],
                             author: UserId,
                             requestContext: RequestContext): Future[Done] = {
      val parallelism = 5
      readJournal
        .currentPersistenceIds()
        .mapAsync(parallelism) { persistenceId =>
          proposalCoordinatorService.getProposal(ProposalId(persistenceId))
        }
        .filter(proposal => proposal.isDefined && proposal.forall(_.status == ProposalStatus.Accepted))
        .map(_.get)
        .mapAsync(parallelism) { proposal =>
          val command = createPatchProposalCommand(proposal, tagsByTheme, tagsByOperation, author, requestContext)
          (proposalCoordinator ? command).mapTo[Option[Proposal]]
        }
        .runForeach { done =>
          logger.debug("tag flow ended with result {}", done)
        }(ActorMaterializer()(actorSystem))
    }

    private val languageByCountry: Map[String, String] = Map("FR" -> "fr", "GB" -> "en", "IT" -> "it")

    private def tagTypeForTag(label: String): TagTypeId = {
      val ACTOR = TagTypeId("982e6860-eb66-407e-bafb-461c2d927478")
      val TARGET = TagTypeId("226070ac-51b0-4e92-883a-f0a24d5b8525")

      label match {
        case l if l.startsWith("action :") => ACTOR
        case l if l.startsWith("cible :")  => TARGET
        case _                             => TagType.LEGACY.tagTypeId
      }
    }

    private def createNewTagsForTheme(themes: Seq[Theme]): Future[Seq[ThemeJoinTagIds]] = {
      val tags: Seq[Tag] = themes.flatMap { theme =>
        theme.tags.map { tag =>
          tag.copy(
            tagTypeId = tagTypeForTag(tag.label),
            operationId = None,
            themeId = Some(theme.themeId),
            country = theme.country,
            language = languageByCountry(theme.country)
          )
        }
      }
      Future.traverse(tags) { tagData =>
        tagService
          .createTag(
            label = tagData.label,
            tagTypeId = tagData.tagTypeId,
            operationId = tagData.operationId,
            themeId = tagData.themeId,
            country = tagData.country,
            language = tagData.language,
            display = tagData.display,
            weight = tagData.weight
          )
          .map(tag => ThemeJoinTagIds(tagData.themeId.get, tag.tagId, tagData.tagId))
      }
    }

    private def createNewTagsForOperation(oldTags: Seq[Tag],
                                          operations: Seq[Operation]): Future[Seq[OperationJoinTagIds]] = {

      val mapOldTags: Map[String, String] = oldTags.map(tag => tag.tagId.value -> tag.label).toMap
      val tags: Seq[Tag] = operations.flatMap { operation =>
        operation.countriesConfiguration.flatMap { countryConfig =>
          countryConfig.tagIds.flatMap { slug =>
            mapOldTags.get(slug.value).map { label =>
              Tag(
                tagId = slug,
                label = label,
                tagTypeId = tagTypeForTag(label),
                operationId = Some(operation.operationId),
                themeId = None,
                country = countryConfig.countryCode,
                language = languageByCountry(countryConfig.countryCode),
                display = TagDisplay.Inherit,
                weight = 0f
              )
            }
          }
        }
      }

      val context: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

      Future.traverse(tags) { tagData =>
        tagService
          .createTag(
            label = tagData.label,
            tagTypeId = tagData.tagTypeId,
            operationId = tagData.operationId,
            themeId = tagData.themeId,
            country = tagData.country,
            language = tagData.language,
            display = tagData.display,
            weight = tagData.weight
          )
          .map(tag => OperationJoinTagIds(tagData.operationId.get, tag.tagId, tagData.tagId))
      }(implicitly, context)
    }

    def createPatchProposalCommand(proposal: Proposal,
                                   tagsByTheme: Seq[ThemeJoinTagIds],
                                   tagsByOperation: Seq[OperationJoinTagIds],
                                   author: UserId,
                                   requestContext: RequestContext): PatchProposalCommand = {
      val associatedTags: Seq[JoinTagIds] = proposal.theme
        .orElse(proposal.operation)
        .map {
          case ThemeId(themeId) => tagsByTheme.filter(_.themeId == ThemeId(themeId))
          case OperationId(operationId) =>
            tagsByOperation.filter(_.operationId == OperationId(operationId))
        }
        .getOrElse(Seq.empty)
      val tags = proposal.tags.flatMap { tagId =>
        associatedTags.find(_.slug == tagId).map(_.tagId)
      }
      PatchProposalCommand(
        proposalId = proposal.proposalId,
        userId = author,
        changes = PatchProposalRequest(tags = Some(tags)),
        requestContext = requestContext
      )
    }

  }
}
