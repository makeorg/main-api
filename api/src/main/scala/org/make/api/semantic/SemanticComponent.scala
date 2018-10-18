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

package org.make.api.semantic

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api.idea.IdeaServiceComponent
import org.make.api.technical.EventBusServiceComponent
import org.make.api.{semantic, ActorSystemComponent}
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.mdedetrich.akka.http.support.CirceHttpSupport

import scala.concurrent.{ExecutionContext, Future}

trait SemanticComponent {
  def semanticService: SemanticService
}

trait SemanticService {
  def indexProposal(indexedProposal: IndexedProposal): Future[Unit]
  def indexProposals(indexedProposals: Seq[IndexedProposal]): Future[Unit]
  def getSimilarIdeas(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarIdea]]
}

case class IndexProposalsWrapper(proposals: Seq[SemanticProposal])
object IndexProposalsWrapper {
  implicit val encoder: Encoder[IndexProposalsWrapper] = deriveEncoder[IndexProposalsWrapper]
  implicit val decoder: Decoder[IndexProposalsWrapper] = deriveDecoder[IndexProposalsWrapper]
}

trait DefaultSemanticComponent extends SemanticComponent {
  this: ActorSystemComponent
    with SemanticConfigurationComponent
    with IdeaServiceComponent
    with EventBusServiceComponent
    with CirceHttpSupport
    with StrictLogging =>

  private val httpThreads = 5
  implicit private val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(httpThreads))

  override lazy val semanticService: SemanticService = new SemanticService {
    private implicit val system: ActorSystem = actorSystem
    private implicit val materializer: ActorMaterializer = ActorMaterializer()

    override def indexProposal(indexedProposal: IndexedProposal): Future[Unit] = {
      Marshal(SemanticProposal(indexedProposal)).to[RequestEntity].flatMap { entity =>
        logger.debug(s"Indexing in semantic API: {}", indexedProposal)
        Http(actorSystem)
          .singleRequest(
            request = HttpRequest(
              method = HttpMethods.POST,
              uri = Uri(s"${semanticConfiguration.url}/similar/index-proposal"),
              entity = entity
            )
          )
          .map((response: HttpMessage) => logger.debug(s"Indexing response: {}", response))
      }
    }

    override def indexProposals(indexedProposals: Seq[IndexedProposal]): Future[Unit] = {
      Marshal(semantic.IndexProposalsWrapper(indexedProposals.map(SemanticProposal.apply))).to[RequestEntity].flatMap {
        entities =>
          logger.debug(s"Indexing in semantic API: {}", indexedProposals)
          Http(actorSystem)
            .singleRequest(
              request = HttpRequest(
                method = HttpMethods.POST,
                uri = Uri(s"${semanticConfiguration.url}/similar/index-proposals"),
                entity = entities
              )
            )
            .map((response: HttpMessage) => logger.debug(s"Indexing response: {}", response))
      }
    }

    override def getSimilarIdeas(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarIdea]] = {
      Marshal(SimilarIdeasRequest(SemanticProposal(indexedProposal), nSimilar)).to[RequestEntity].flatMap { entity =>
        val response = Http(actorSystem)
          .singleRequest(
            request = HttpRequest(
              method = HttpMethods.POST,
              uri = Uri(s"${semanticConfiguration.url}/similar/get-similar-ideas"),
              entity = entity
            )
          )

        response.flatMap {
          case HttpResponse(StatusCodes.OK, _, responseEntity, _) =>
            Unmarshal(responseEntity).to[SimilarIdeasResponse].flatMap { similarIdeas =>
              logSimilarIdeas(indexedProposal, similarIdeas.similar, similarIdeas.algoLabel.getOrElse("Semantic API"))
              buildSimilarIdeas(similarIdeas.similar)
            }

          case error => Future.failed(new RuntimeException(s"Error with semantic request: $error"))
        }
      }
    }
  }

  def logSimilarIdeas(indexedProposal: IndexedProposal,
                      similarProposals: Seq[ScoredProposal],
                      algoLabel: String): Unit = {
    eventBusService.publish(
      PredictDuplicateEvent(
        proposalId = indexedProposal.id,
        predictedDuplicates = similarProposals.map(_.proposal.id),
        predictedScores = similarProposals.map(_.score.score),
        algoLabel = algoLabel
      )
    )
  }

  def buildSimilarIdeas(similarIdeas: Seq[ScoredProposal]): Future[Seq[SimilarIdea]] = {
    val similarIdeaIds: Seq[IdeaId] = similarIdeas.flatMap(p => p.proposal.ideaId)

    // TODO: avoid calling .get on options
    ideaService
      .fetchAllByIdeaIds(similarIdeaIds)
      .map { ideas =>
        val ideasById: Map[IdeaId, Idea] = ideas.map(idea => (idea.ideaId, idea)).toMap[IdeaId, Idea]

        similarIdeas.map(
          p =>
            SimilarIdea(
              ideaId = p.proposal.ideaId.get,
              ideaName = ideasById(p.proposal.ideaId.get).name,
              proposalId = p.proposal.id,
              proposalContent = p.proposal.content,
              score = p.score.score
          )
        )
      }
  }

}

final case class SemanticProposal(@ApiModelProperty(dataType = "string") id: ProposalId,
                                  questionId: QuestionId,
                                  language: Language,
                                  ideaId: Option[IdeaId],
                                  status: ProposalStatus,
                                  content: String)

object SemanticProposal {
  implicit val encoder: ObjectEncoder[SemanticProposal] = deriveEncoder[SemanticProposal]
  implicit val decoder: Decoder[SemanticProposal] = deriveDecoder[SemanticProposal]

  def apply(indexedProposal: IndexedProposal): SemanticProposal = {
    new SemanticProposal(
      id = indexedProposal.id,
      questionId = indexedProposal.questionId.getOrElse(QuestionId("None")),
      language = indexedProposal.language,
      ideaId = indexedProposal.ideaId,
      content = indexedProposal.content,
      status = indexedProposal.status
    )
  }
}

case class SimilarIdeasRequest(proposal: SemanticProposal, nSimilar: Int)

object SimilarIdeasRequest {
  implicit val encoder: Encoder[SimilarIdeasRequest] = deriveEncoder[SimilarIdeasRequest]
}

case class SimilarityScore(score: Double)

object SimilarityScore {
  implicit val encoder: Encoder[SimilarityScore] = _.score.asJson

  implicit val decoder: Decoder[SimilarityScore] = _.as[Double] match {
    case Right(value)  => Right(SimilarityScore(value))
    case Left(failure) => Left(failure)
  }
}

@ApiModel
case class ScoredProposal(proposal: SemanticProposal, @ApiModelProperty(dataType = "string") score: SimilarityScore)

object ScoredProposal {
  implicit val decoder: Decoder[ScoredProposal] = deriveDecoder[ScoredProposal]
}

case class SimilarIdeasResponse(similar: Seq[ScoredProposal], algoLabel: Option[String])

object SimilarIdeasResponse {
  implicit val decoder: Decoder[SimilarIdeasResponse] = deriveDecoder[SimilarIdeasResponse]
}

final case class SimilarIdea(ideaId: IdeaId,
                             ideaName: String,
                             proposalId: ProposalId,
                             proposalContent: String,
                             score: Double)

object SimilarIdea {
  implicit val encoder: ObjectEncoder[SimilarIdea] = deriveEncoder[SimilarIdea]
  implicit val decoder: Decoder[SimilarIdea] = deriveDecoder[SimilarIdea]
}
