package org.make.api.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.get.GetResponse
import com.typesafe.scalalogging.StrictLogging
import org.make.api.Predef._
import org.make.core.proposition.PropositionEvent.{PropositionEventWrapper, PropositionProposed, PropositionUpdated, PropositionViewed}
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class PropositionElasticsearch(
                                     id: UUID,
                                     citizenId: UUID,
                                     createdAt: ZonedDateTime,
                                     updatedAt: ZonedDateTime,
                                     content: String,
                                     nbVotesAgree: Int,
                                     nbVotesDisagree: Int,
                                     nbVotesUnsure: Int
                                   )


object PropositionElasticsearch extends StrictLogging {

  object shapePropositionEvent extends Poly1 {

    def fromViewed(p: PropositionViewed): () => Future[Option[PropositionElasticsearch]] =
      () => Future.successful(None)

    def fromProposed(p: PropositionProposed): () => Future[Option[PropositionElasticsearch]] = {
      logger.debug("In shape as PropositionProposed" + p.toString)
      () => Future.successful(Some(PropositionElasticsearch(
        id = UUID.fromString(p.id.value),
        citizenId = UUID.fromString(p.citizenId.value),
        createdAt = p.createdAt.toUTC,
        updatedAt = p.createdAt.toUTC,
        content = p.content,
        nbVotesAgree = 0,
        nbVotesDisagree = 0,
        nbVotesUnsure = 0
      )))
    }

    def fromUpdated(p: PropositionUpdated)(implicit api: ElasticsearchAPI): () => Future[Option[PropositionElasticsearch]] = {
      logger.debug("In shape as PropositionUpdated")
      val eventualMaybePropositionElasticsearch = api.getPropositionById(p.id) map {
        _.map { actual =>
          PropositionElasticsearch(
            id = actual.id,
            citizenId = actual.citizenId,
            createdAt = actual.createdAt.toUTC,
            updatedAt = p.updatedAt.toUTC,
            content = p.content,
            nbVotesAgree = actual.nbVotesAgree,
            nbVotesDisagree = actual.nbVotesDisagree,
            nbVotesUnsure = actual.nbVotesUnsure
          )
        }
      }
      () => eventualMaybePropositionElasticsearch
    }

    implicit def caseViewed = at[PropositionViewed](fromViewed)
    implicit def caseProposed = at[PropositionProposed](fromProposed)
    implicit def caseUpdated(implicit api: ElasticsearchAPI) = at[PropositionUpdated](fromUpdated)
  }

  def shape(implicit api: ElasticsearchAPI): PartialFunction[AnyRef, Future[Option[PropositionElasticsearch]]] = {
    case e: PropositionEventWrapper =>
      logger.debug("In shape as PropositionEventWrapper: Type: " + e.eventType + " Event: " + e)
      val result =
        e.event
        .map(shapePropositionEvent)
      logger.debug("In shape as PropositionEventWrapper: Result Type: " + result.toString)
      result
        .select[() => Future[Option[PropositionElasticsearch]]]
        .getOrElse(() => Future.successful(None))()
    case res: GetResponse =>
      logger.debug("In shape as GetResponse")
      res.sourceAsMap match {
        case x if x.isEmpty =>
          logger.debug("In shape as GetResponse: Map.empty" + res.toString)
          Future.successful(None)
        case source: Map[String, AnyRef] =>
          logger.debug("In shape as GetResponse: source: " + source.toString)
          Future.successful(Some(PropositionElasticsearch(
            id = UUID.fromString(source.getOrElse("id", "NotFound").toString),
            citizenId = UUID.fromString(source.getOrElse("citizenId", "NotFound").toString),
            createdAt = ZonedDateTime.parse(source.getOrElse("createdAt", ZonedDateTime.now).toString).toUTC,
            updatedAt = ZonedDateTime.parse(source.getOrElse("updatedAt", ZonedDateTime.now).toString).toUTC,
            content = source.getOrElse("content", "No content").toString,
            nbVotesAgree = source.getOrElse("nbVotesAgree", 0).asInstanceOf[Int],
            nbVotesDisagree = source.getOrElse("nbVotesDisagree", 0).asInstanceOf[Int],
            nbVotesUnsure = source.getOrElse("nbVotesUnsure", 0).asInstanceOf[Int]
          )))
      }
    case _ =>
      logger.debug("In shape as _")
      Future.successful(None)
  }

}
