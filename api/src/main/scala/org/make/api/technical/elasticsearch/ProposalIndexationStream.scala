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

package org.make.api.technical.elasticsearch

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZonedDateTime}

import akka.stream.FlowShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import akka.{Done, NotUsed}
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.proposal.{
  ProposalCoordinatorServiceComponent,
  ProposalScorerHelper,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.semantic.SemanticComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.{
  Author,
  IndexedOrganisationInfo,
  IndexedProposal,
  IndexedScores,
  IndexedVote,
  Context => ProposalContext
}
import org.make.core.reference.{Country, Language}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalIndexationStream
    extends IndexationStream
    with ProposalCoordinatorServiceComponent
    with UserServiceComponent
    with TagServiceComponent
    with ProposalSearchEngineComponent
    with SemanticComponent
    with StrictLogging {

  object ProposalStream {
    val maybeIndexedProposal: Flow[ProposalId, Option[IndexedProposal], NotUsed] =
      Flow[ProposalId].mapAsync(parallelism)(proposalId => getIndexedProposal(proposalId))

    def runIndexProposals(proposalIndexName: String): Flow[Seq[IndexedProposal], Done, NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(parallelism)(proposals => executeIndexProposals(proposals, proposalIndexName))

    val findOrElseIndexedProposal: Flow[IndexedProposal, ProposalFlow, NotUsed] =
      Flow[IndexedProposal]
        .mapAsync(parallelism) { proposal =>
          elasticsearchProposalAPI.findProposalById(proposal.id).map {
            case Some(_) => UpdateProposalFlow(proposal)
            case _       => IndexProposalFlow(proposal)
          }
        }

    val indexProposals: Flow[Seq[IndexedProposal], Seq[IndexedProposal], NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(singleAsync) { proposals =>
        elasticsearchProposalAPI.indexProposals(proposals)
      }

    val updateProposals: Flow[Seq[IndexedProposal], Seq[IndexedProposal], NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(singleAsync) { proposals =>
        elasticsearchProposalAPI.updateProposals(proposals)
      }

    val semanticIndex: Flow[Seq[IndexedProposal], Done, NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(parallelism) { proposals =>
        semanticService.indexProposals(proposals).map(_ => Done)
      }

    def flowIndexProposals(proposalIndexName: String): Flow[ProposalId, Done, NotUsed] =
      maybeIndexedProposal
        .via(filterIsDefined[IndexedProposal])
        .via(grouped[IndexedProposal])
        .via(runIndexProposals(proposalIndexName))

    val indexOrUpdateFlow: Flow[ProposalId, Seq[IndexedProposal], NotUsed] =
      Flow.fromGraph[ProposalId, Seq[IndexedProposal], NotUsed](GraphDSL.create() {
        implicit builder: GraphDSL.Builder[NotUsed] =>
          val source = builder.add(maybeIndexedProposal)
          val partition = builder.add(Partition[ProposalFlow](outputPorts = 2, partitioner = {
            case IndexProposalFlow(_)  => 0
            case UpdateProposalFlow(_) => 1
          }))
          val merge = builder.add(Merge[Seq[IndexedProposal]](2))

          val filterIndex: Flow[ProposalFlow, IndexedProposal, NotUsed] =
            Flow[ProposalFlow].filter {
              case IndexProposalFlow(_)  => true
              case UpdateProposalFlow(_) => false
            }.map(_.proposal)

          val filterUpdate: Flow[ProposalFlow, IndexedProposal, NotUsed] =
            Flow[ProposalFlow].filter {
              case IndexProposalFlow(_)  => false
              case UpdateProposalFlow(_) => true
            }.map(_.proposal)

          source.out ~> filterIsDefined[IndexedProposal] ~> findOrElseIndexedProposal ~> partition.in

          partition.out(0) ~> filterIndex  ~> grouped[IndexedProposal] ~> indexProposals  ~> merge
          partition.out(1) ~> filterUpdate ~> grouped[IndexedProposal] ~> updateProposals ~> merge

          FlowShape(source.in, merge.out)
      })
  }

  private def getIndexedProposal(proposalId: ProposalId): Future[Option[IndexedProposal]] = {
    val maybeResult: OptionT[Future, IndexedProposal] = for {
      proposal <- OptionT(proposalCoordinatorService.getProposal(proposalId))
      user     <- OptionT(userService.getUser(proposal.author))
      tags     <- OptionT(tagService.retrieveIndexedTags(proposal.tags))
    } yield {
      val isBeforeContextSourceFeature: Boolean =
        proposal.createdAt.exists(_.isBefore(ZonedDateTime.parse("2018-09-01T00:00:00Z")))

      IndexedProposal(
        id = proposal.proposalId,
        userId = proposal.author,
        content = proposal.content,
        slug = proposal.slug,
        status = proposal.status,
        createdAt = proposal.createdAt.get,
        updatedAt = proposal.updatedAt,
        votes = proposal.votes.map(IndexedVote.apply),
        scores = IndexedScores(
          engagement = ProposalScorerHelper.engagement(proposal),
          adhesion = ProposalScorerHelper.adhesion(proposal),
          realistic = ProposalScorerHelper.realistic(proposal),
          topScore = ProposalScorerHelper.topScore(proposal),
          controversy = ProposalScorerHelper.controversy(proposal),
          rejection = ProposalScorerHelper.rejection(proposal)
        ),
        context = Some(
          ProposalContext(
            operation = proposal.creationContext.operationId,
            source = proposal.creationContext.source.filter(!_.isEmpty) match {
              case None if isBeforeContextSourceFeature => Some("core")
              case other                                => other
            },
            location = proposal.creationContext.location,
            question = proposal.creationContext.question
          )
        ),
        trending = None,
        labels = proposal.labels.map(_.value),
        author = Author(
          firstName = user.firstName,
          organisationName = user.organisationName,
          postalCode = user.profile.flatMap(_.postalCode),
          age = user.profile
            .flatMap(_.dateOfBirth)
            .map(date => ChronoUnit.YEARS.between(date, LocalDate.now()).toInt),
          avatarUrl = user.profile.flatMap(_.avatarUrl)
        ),
        organisations = proposal.organisations.map(IndexedOrganisationInfo.apply),
        country = proposal.country.getOrElse(Country("FR")),
        language = proposal.language.getOrElse(Language("fr")),
        themeId = proposal.theme,
        tags = tags,
        ideaId = proposal.idea,
        operationId = proposal.operation
      )
    }

    maybeResult.value
  }

  private def executeIndexProposals(proposals: Seq[IndexedProposal], indexName: String): Future[Done] = {
    elasticsearchProposalAPI
      .indexProposals(proposals, Some(IndexAndType(indexName, ProposalSearchEngine.proposalIndexName)))
      .flatMap { proposals =>
        semanticService.indexProposals(proposals).map(_ => Done)
      }
      .recoverWith {
        case e =>
          logger.error("Indexing proposals in proposal index OR in semantic index failed", e)
          Future.successful(Done)
      }
  }

}

sealed trait ProposalFlow {
  val proposal: IndexedProposal
}
case class IndexProposalFlow(override val proposal: IndexedProposal) extends ProposalFlow
case class UpdateProposalFlow(override val proposal: IndexedProposal) extends ProposalFlow
