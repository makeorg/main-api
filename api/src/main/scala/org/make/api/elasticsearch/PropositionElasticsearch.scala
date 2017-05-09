package org.make.api.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.get.GetResponse
import org.make.core.proposition.PropositionEvent.{PropositionEventWrapper, PropositionProposed, PropositionUpdated}

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


object PropositionElasticsearch {

  def shape: PartialFunction[AnyRef, Future[Option[PropositionElasticsearch]]] = {
    case e: PropositionEventWrapper => e.event match {
      case p: PropositionUpdated => ElasticsearchAPI.api.getPropositionById(p.id.value) map {
        case Some(actual: PropositionElasticsearch) =>
          Some(PropositionElasticsearch(
            id = actual.id,
            citizenId = actual.citizenId,
            createdAt = actual.createdAt,
            updatedAt = p.updatedAt,
            content = p.content,
            nbVotesAgree = actual.nbVotesAgree,
            nbVotesDisagree = actual.nbVotesDisagree,
            nbVotesUnsure = actual.nbVotesUnsure
          ))
        case None => None
      }
      case p: PropositionProposed =>
        Future(Some(PropositionElasticsearch(
          id = UUID.fromString(p.id.value),
          citizenId = UUID.fromString(p.citizenId.value),
          createdAt = p.createdAt,
          updatedAt = p.createdAt,
          content = p.content,
          nbVotesAgree = 0,
          nbVotesDisagree = 0,
          nbVotesUnsure = 0
        )))
    }
    case res: GetResponse => res.storedFieldsAsMap match {
      case x if x == Map.empty => Future(None)
      case fields: Map[String, AnyRef] =>
        Future(Some(PropositionElasticsearch(
          id = UUID.fromString(fields.getOrElse("id", "NotFound").toString),
          citizenId = UUID.fromString(fields.getOrElse("citizenId", "NotFound").toString),
          createdAt = fields.getOrElse("createdAt", ZonedDateTime.now).asInstanceOf[ZonedDateTime],
          updatedAt = fields.getOrElse("updatedAt", ZonedDateTime.now).asInstanceOf[ZonedDateTime],
          content = fields.getOrElse("content", "No content").toString,
          nbVotesAgree = fields.getOrElse("nbVotesAgree", 0).asInstanceOf[Int],
          nbVotesDisagree = fields.getOrElse("nbVotesDisagree", 0).asInstanceOf[Int],
          nbVotesUnsure = fields.getOrElse("nbVotesUnsure", 0).asInstanceOf[Int]
        )))
    }
    case _ => Future(None)
  }

}
