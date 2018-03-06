package org.make.api.semantic

import java.util.concurrent.Executors

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.swagger.annotations.ApiModelProperty
import org.make.api.ActorSystemComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.technical.EventBusServiceComponent
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.IndexedProposal

import scala.concurrent.{ExecutionContext, Future}

trait SemanticComponent {
  def semanticService: SemanticService
}

trait SemanticService {
  def indexProposal(indexedProposal: IndexedProposal): Future[Unit]
  def getSimilarProposals(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarProposal]]
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

  implicit lazy val actorMaterialzer: ActorMaterializer = materializer

  override lazy val semanticService: SemanticService = new SemanticService {

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

    override def getSimilarProposals(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarProposal]] = {
      Marshal(SimilarProposalsRequest(SemanticProposal(indexedProposal), nSimilar)).to[RequestEntity].flatMap {
        entity =>
          val response = Http(actorSystem)
            .singleRequest(
              request = HttpRequest(
                method = HttpMethods.POST,
                uri = Uri(s"${semanticConfiguration.url}/similar/get-similar-proposals"),
                entity = entity
              )
            )

          response.flatMap {
            case HttpResponse(StatusCodes.OK, _, entity, _) => {
              Unmarshal(entity).to[SimilarProposalsResponse].flatMap { similarProposals =>
                eventBusService.publish(
                  PredictDuplicateEvent(
                    proposalId = indexedProposal.id,
                    predictedDuplicates = similarProposals.similar.map(_._1.id),
                    predictedScores = similarProposals.similar.map(_._2.score),
                    algoLabel = "Semantic API"
                  )
                )

                val similarIdeaIds: Seq[IdeaId] = similarProposals.similar.map {
                  case (semanticProposal, _) => IdeaId(semanticProposal.ideaId.get)
                }
                ideaService
                  .fetchAllByIdeaIds(similarIdeaIds)
                  .map { ideas =>
                    val ideasById: Map[IdeaId, Idea] = ideas.map(idea => (idea.ideaId, idea)).toMap[IdeaId, Idea]

                    similarProposals.similar.map {
                      case (semanticProposal, similarityScore) =>
                        new SimilarProposal(
                          ideaId = IdeaId(semanticProposal.ideaId.get),
                          ideaName = ideasById(IdeaId(semanticProposal.ideaId.get)).name,
                          proposalId = ProposalId(semanticProposal.ideaId.get),
                          proposalContent = semanticProposal.content,
                          score = similarityScore.score
                        )
                    }
                  }
              }
            }

            case response => throw new RuntimeException(s"Error with semantic request: $response")
          }
      }
    }
  }
}

final case class SemanticProposal(@ApiModelProperty(dataType = "string") id: ProposalId,
                                  source: String,
                                  operationId: Option[String],
                                  themeId: Option[String],
                                  country: String,
                                  language: String,
                                  ideaId: Option[String],
                                  content: String)

object SemanticProposal {
  implicit val encoder: ObjectEncoder[SemanticProposal] = deriveEncoder[SemanticProposal]
  implicit val decoder: Decoder[SemanticProposal] = deriveDecoder[SemanticProposal]

  def apply(indexedProposal: IndexedProposal): SemanticProposal = {
    new SemanticProposal(
      id = indexedProposal.id,
      source = indexedProposal.context match {
        case Some(context) => context.source.getOrElse("core")
        case None          => "core"
      },
      operationId = indexedProposal.operationId.flatMap(id => Option[String](id.value)),
      themeId = indexedProposal.themeId.flatMap(id         => Option[String](id.value)),
      country = indexedProposal.country,
      language = indexedProposal.language,
      ideaId = indexedProposal.ideaId.flatMap(id => Option[String](id.value)),
      content = indexedProposal.content
    )
  }
}

case class SimilarProposalsRequest(proposal: SemanticProposal, nSimilar: Int)

object SimilarProposalsRequest {
  implicit val encoder: Encoder[SimilarProposalsRequest] = deriveEncoder[SimilarProposalsRequest]
}

case class SimilarityScore(score: Double)

object SimilarityScore {
  implicit val encoder: Encoder[SimilarityScore] = new Encoder[SimilarityScore] {
    final def apply(a: SimilarityScore): Json = a.score.asJson
  }

  implicit val decoder: Decoder[SimilarityScore] = new Decoder[SimilarityScore] {
    final def apply(c: HCursor): Decoder.Result[SimilarityScore] = c.as[Double] match {
      case Right(value)  => Right(SimilarityScore(value))
      case Left(failure) => Left(failure)
    }
  }
}
case class SimilarProposalsResponse(similar: Seq[(SemanticProposal, SimilarityScore)])

object SimilarProposalsResponse {
  implicit val decoder: Decoder[SimilarProposalsResponse] = deriveDecoder[SimilarProposalsResponse]
}

final case class SimilarProposal(ideaId: IdeaId,
                                 ideaName: String,
                                 proposalId: ProposalId,
                                 proposalContent: String,
                                 score: Double)

object SimilarProposal {
  implicit val encoder: ObjectEncoder[SimilarProposal] = deriveEncoder[SimilarProposal]
  implicit val decoder: Decoder[SimilarProposal] = deriveDecoder[SimilarProposal]
}
