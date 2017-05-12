package org.make.api.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.get.GetResponse
import com.typesafe.scalalogging.StrictLogging
import org.make.api.Predef._

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

  def shape: PartialFunction[AnyRef, Option[PropositionElasticsearch]] = {
    case res: GetResponse =>
      logger.debug("In shape as GetResponse")
      res.sourceAsMap match {
        case x if x.isEmpty =>
          logger.debug("In shape as GetResponse: Map.empty" + res.toString)
          None
        case source: Map[String, AnyRef] =>
          logger.debug("In shape as GetResponse: source: " + source.toString)
          Some(PropositionElasticsearch(
            id = UUID.fromString(source.getOrElse("id", "NotFound").toString),
            citizenId = UUID.fromString(source.getOrElse("citizenId", "NotFound").toString),
            createdAt = ZonedDateTime.parse(source.getOrElse("createdAt", ZonedDateTime.now).toString).toUTC,
            updatedAt = ZonedDateTime.parse(source.getOrElse("updatedAt", ZonedDateTime.now).toString).toUTC,
            content = source.getOrElse("content", "No content").toString,
            nbVotesAgree = source.getOrElse("nbVotesAgree", 0).asInstanceOf[Int],
            nbVotesDisagree = source.getOrElse("nbVotesDisagree", 0).asInstanceOf[Int],
            nbVotesUnsure = source.getOrElse("nbVotesUnsure", 0).asInstanceOf[Int]
          ))
      }
    case _ =>
      logger.debug("In shape as _")
      None
  }

}
