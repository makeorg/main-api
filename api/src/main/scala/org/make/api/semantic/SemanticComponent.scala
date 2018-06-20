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
import io.swagger.annotations.{ApiModel, ApiModelProperty}
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
  def getSimilarIdeas(indexedProposal: IndexedProposal, nSimilar: Int): Future[Seq[SimilarIdea]]
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
    private implicit val system = actorSystem
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
          case HttpResponse(StatusCodes.OK, _, entity, _) => {
            Unmarshal(entity).to[SimilarIdeasResponse].flatMap { similarIdeas =>
              logSimilarIdeas(indexedProposal, similarIdeas.similar, similarIdeas.algoLabel.getOrElse("Semantic API"))
              buildSimilarIdeas(similarIdeas.similar)
            }
          }

          case response => Future.failed(new RuntimeException(s"Error with semantic request: $response"))
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
    val similarIdeaIds: Seq[IdeaId] = similarIdeas.flatMap(p => p.proposal.ideaId).map(IdeaId(_))

    ideaService
      .fetchAllByIdeaIds(similarIdeaIds)
      .map { ideas =>
        val ideasById: Map[IdeaId, Idea] = ideas.map(idea => (idea.ideaId, idea)).toMap[IdeaId, Idea]

        similarIdeas.map(
          p =>
            SimilarIdea(
              ideaId = IdeaId(p.proposal.ideaId.get),
              ideaName = ideasById(IdeaId(p.proposal.ideaId.get)).name,
              proposalId = p.proposal.id,
              proposalContent = p.proposal.content,
              score = p.score.score
          )
        )
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
      source = indexedProposal.context.flatMap(_.source).getOrElse("core"),
      operationId = indexedProposal.operationId.map(_.value),
      themeId = indexedProposal.themeId.map(_.value),
      country = indexedProposal.country,
      language = indexedProposal.language,
      ideaId = indexedProposal.ideaId.map(_.value),
      content = indexedProposal.content
    )
  }
}

final case class SemanticIdea(@ApiModelProperty(dataType = "string") id: ProposalId,
                              source: String,
                              operationId: Option[String],
                              themeId: Option[String],
                              country: String,
                              language: String,
                              ideaId: String,
                              content: String)

object SemanticIdea {
  implicit val decoder: Decoder[SemanticIdea] = deriveDecoder[SemanticIdea]
}

case class SimilarIdeasRequest(proposal: SemanticProposal, nSimilar: Int)

object SimilarIdeasRequest {
  implicit val encoder: Encoder[SimilarIdeasRequest] = deriveEncoder[SimilarIdeasRequest]
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
