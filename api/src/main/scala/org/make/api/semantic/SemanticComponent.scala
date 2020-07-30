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

import java.net.URL
import java.util.concurrent.Executors

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, OverflowStrategy, QueueOfferResult}
import cats.data.OptionT
import cats.instances.future._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.EventBusServiceComponent
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal.{Proposal, ProposalId, ProposalStatus}
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.tag.{TagId, TagTypeId}

import scala.annotation.meta.field
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait SemanticComponent {
  def semanticService: SemanticService
}

trait SemanticService {
  def indexProposal(indexedProposal: IndexedProposal): Future[Unit]
  def indexProposals(indexedProposals: Seq[IndexedProposal]): Future[Unit]
  def getSimilarIdeas(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarIdea]]
  def getPredictedTagsForProposal(proposal: Proposal): Future[GetPredictedTagsResponse]
}

case class IndexProposalsWrapper(proposals: Seq[SemanticProposal])
object IndexProposalsWrapper {
  implicit val encoder: Encoder[IndexProposalsWrapper] = deriveEncoder[IndexProposalsWrapper]
  implicit val decoder: Decoder[IndexProposalsWrapper] = deriveDecoder[IndexProposalsWrapper]
}

trait DefaultSemanticComponent extends SemanticComponent with ErrorAccumulatingCirceSupport with StrictLogging {
  this: ActorSystemComponent
    with SemanticConfigurationComponent
    with IdeaServiceComponent
    with EventBusServiceComponent
    with QuestionServiceComponent
    with TagServiceComponent =>

  override lazy val semanticService: SemanticService = new DefaultSemanticService

  class DefaultSemanticService extends SemanticService {

    private val httpThreads = 12
    implicit private val executionContext: ExecutionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(httpThreads))

    lazy val semanticUrl = new URL(semanticConfiguration.url)

    lazy val semanticHttpFlow: Flow[
      (HttpRequest, Promise[HttpResponse]),
      (Try[HttpResponse], Promise[HttpResponse]),
      Http.HostConnectionPool
    ] =
      Http(actorSystem)
        .cachedHostConnectionPool[Promise[HttpResponse]](
          host = semanticUrl.getHost,
          port = semanticUrl.getPort,
          settings = ConnectionPoolSettings(actorSystem).withMaxConnections(httpThreads)
        )

    private lazy val semanticBufferSize = semanticConfiguration.httpBufferSize

    lazy val semanticQueue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
      .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = semanticBufferSize, OverflowStrategy.backpressure)
      .via(semanticHttpFlow)
      .withAttributes(ActorAttributes.dispatcher(api.semanticDispatcher))
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()

    private def doHttpCall(request: HttpRequest): Future[HttpResponse] = {
      val promise = Promise[HttpResponse]()
      semanticQueue.offer((request, promise)).flatMap {
        case QueueOfferResult.Enqueued    => promise.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed =>
          Future
            .failed(
              new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
            )
      }
    }

    override def indexProposal(indexedProposal: IndexedProposal): Future[Unit] = {
      Marshal(SemanticProposal(indexedProposal)).to[RequestEntity].flatMap { entity =>
        val request = HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${semanticConfiguration.url}/similar/index-proposal"),
          entity = entity
        )
        doHttpCall(request)
          .map((response: HttpMessage) => logger.debug(s"Indexing response: {}", response))
      }
    }

    override def indexProposals(indexedProposals: Seq[IndexedProposal]): Future[Unit] = {
      Marshal(IndexProposalsWrapper(indexedProposals.map(SemanticProposal.apply))).to[RequestEntity].flatMap {
        entities =>
          val request = HttpRequest(
            method = HttpMethods.POST,
            uri = Uri(s"${semanticConfiguration.url}/similar/index-proposals"),
            entity = entities
          )
          doHttpCall(request)
            .map((response: HttpMessage) => logger.debug(s"Indexing response: {}", response))
      }
    }

    override def getSimilarIdeas(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarIdea]] = {
// TODO: un-comment me when similars work well
//      Marshal(SimilarIdeasRequest(SemanticProposal(indexedProposal), nSimilar)).to[RequestEntity].flatMap { entity =>
//        val request = HttpRequest(
//          method = HttpMethods.POST,
//          uri = Uri(s"${semanticConfiguration.url}/similar/get-similar-ideas"),
//          entity = entity
//        )
//
//        doHttpCall(request).flatMap {
//          case HttpResponse(StatusCodes.OK, _, responseEntity, _) =>
//            Unmarshal(responseEntity).to[SimilarIdeasResponse].flatMap { similarIdeas =>
//              logSimilarIdeas(indexedProposal, similarIdeas.similar, similarIdeas.algoLabel.getOrElse("Semantic API"))
//              buildSimilarIdeas(similarIdeas.similar)
//            }
//
//          case error => Future.failed(new RuntimeException(s"Error with semantic request: $error"))
//        }
//      }
      Future.successful(Seq.empty)
    }

    override def getPredictedTagsForProposal(proposal: Proposal): Future[GetPredictedTagsResponse] = {
      (for {
        questionId <- OptionT(Future.successful(proposal.questionId))
        question   <- OptionT(questionService.getQuestion(questionId))
      } yield {
        val modelName = "auto"
        Marshal(
          GetPredictedTagsRequest(
            GetPredictedTagsProposalRequest(
              proposal.proposalId,
              questionId,
              question.language.value,
              proposal.status,
              proposal.content
            ),
            modelName = modelName
          )
        ).to[RequestEntity].flatMap { entity =>
          val request = HttpRequest(
            method = HttpMethods.POST,
            uri = Uri(s"${semanticConfiguration.url}/tags/predict"),
            entity = entity
          )

          doHttpCall(request).flatMap {
            case HttpResponse(StatusCodes.OK, _, responseEntity, _) =>
              Unmarshal(responseEntity).to[GetPredictedTagsResponse]
            case error => Future.failed(new RuntimeException(s"Error with semantic request: $error"))
          }
        }
      }).value.flatMap {
        case Some(future) => future
        case _ =>
          logger.warn(s"Cannot get predicted tags for proposal ${proposal.proposalId} because question is missing")
          Future.successful(GetPredictedTagsResponse.none)
      }
    }

    def logSimilarIdeas(
      indexedProposal: IndexedProposal,
      similarProposals: Seq[ScoredProposal],
      algoLabel: String
    ): Unit = {
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

}

final case class SemanticProposal(
  @ApiModelProperty(dataType = "string") id: ProposalId,
  questionId: QuestionId,
  language: Language,
  ideaId: Option[IdeaId],
  status: ProposalStatus,
  content: String
)

object SemanticProposal {
  implicit val encoder: Encoder[SemanticProposal] = deriveEncoder[SemanticProposal]
  implicit val decoder: Decoder[SemanticProposal] = deriveDecoder[SemanticProposal]

  def apply(indexedProposal: IndexedProposal): SemanticProposal = {
    new SemanticProposal(
      id = indexedProposal.id,
      questionId = indexedProposal.question.map(_.questionId).getOrElse(QuestionId("None")),
      language = indexedProposal.question.map(_.language).getOrElse(Language("fr")),
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

final case class SimilarIdea(
  @(ApiModelProperty @field)(dataType = "string", example = "2a774774-33ca-41a3-a0fa-65931397fbfc")
  ideaId: IdeaId,
  ideaName: String,
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  proposalId: ProposalId,
  proposalContent: String,
  score: Double
)

object SimilarIdea {
  implicit val encoder: Encoder[SimilarIdea] = deriveEncoder[SimilarIdea]
  implicit val decoder: Decoder[SimilarIdea] = deriveDecoder[SimilarIdea]
}

case class GetPredictedTagsProposalRequest(
  id: ProposalId,
  questionId: QuestionId,
  language: String,
  status: ProposalStatus,
  content: String
)

object GetPredictedTagsProposalRequest {
  implicit val encoder: Encoder[GetPredictedTagsProposalRequest] = deriveEncoder[GetPredictedTagsProposalRequest]
}

case class GetPredictedTagsRequest(proposal: GetPredictedTagsProposalRequest, modelName: String)

object GetPredictedTagsRequest {
  implicit val encoder: Encoder[GetPredictedTagsRequest] = deriveEncoder[GetPredictedTagsRequest]
}

case class PredictedTag(tagId: TagId, tagTypeId: TagTypeId, tagLabel: String, tagTypeLabel: String, score: Double)

object PredictedTag {
  implicit val decoder: Decoder[PredictedTag] = deriveDecoder[PredictedTag]
}

case class GetPredictedTagsResponse(tags: Seq[PredictedTag], modelName: String)

object GetPredictedTagsResponse {
  implicit val decoder: Decoder[GetPredictedTagsResponse] = deriveDecoder[GetPredictedTagsResponse]

  val none = GetPredictedTagsResponse(Seq.empty, "none")
}
