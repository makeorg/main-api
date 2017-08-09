package org.make.api.technical.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.SearchHit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.Predef._
import org.make.core.proposal.ProposalEvent.ProposalProposed
import org.make.core.tag.{Tag, TagId}
import org.make.core.theme.{Theme, ThemeId}

case class ProposalElasticsearch(id: UUID,
                                 userId: UUID,
                                 content: String,
                                 slug: String,
                                 createdAt: ZonedDateTime,
                                 updatedAt: ZonedDateTime,
                                 countVotesAgree: Int,
                                 countVotesDisagree: Int,
                                 countVotesUnsure: Int,
                                 support: String,
                                 context: String,
                                 authorFirstName: String,
                                 authorPostalCode: String,
                                 authorAge: Int,
                                 themes: Seq[Theme],
                                 tags: Seq[Tag])

object ProposalElasticsearch extends StrictLogging {

  def shape: PartialFunction[AnyRef, Option[ProposalElasticsearch]] = {
    case p: ProposalProposed => shapeFromProposalProposed(p)
    case res: GetResponse    => shapeFromGetResponse(res)
    case res: SearchHit      => shapeFromSource(res.sourceAsMap)
    case _ =>
      logger.debug("In shape as _")
      None
  }

  private def shapeFromProposalProposed(p: ProposalProposed): Option[ProposalElasticsearch] = {
    Some(
      ProposalElasticsearch(
        id = UUID.fromString(p.id.value),
        userId = UUID.fromString(p.userId.value),
        content = p.content,
        slug = "",
        createdAt = p.createdAt.toUTC,
        updatedAt = p.createdAt.toUTC,
        countVotesAgree = 0,
        countVotesDisagree = 0,
        countVotesUnsure = 0,
        support = "",
        context = "",
        authorFirstName = "",
        authorPostalCode = "",
        authorAge = 0,
        themes = Seq(),
        tags = Seq()
      )
    )
  }

  private def shapeFromGetResponse(res: GetResponse): Option[ProposalElasticsearch] = {
    logger.debug("In shape as GetResponse")
    res.sourceAsMap match {
      case x if x.isEmpty =>
        logger.debug("In shape as GetResponse: Map.empty" + res.toString)
        None
      case source: Map[String, AnyRef] =>
        logger.debug("In shape as GetResponse: source: " + source.toString)
        shapeFromSource(source)
    }
  }

  private def shapeFromSource(source: Map[String, AnyRef]): Option[ProposalElasticsearch] = {
    Some(
      ProposalElasticsearch(
        id = UUID.fromString(source.getOrElse("id", "NotFound").toString),
        userId = UUID.fromString(source.getOrElse("userId", "NotFound").toString),
        content = source.getOrElse("content", "NotFound").toString,
        slug = source.getOrElse("slug", "NotFound").toString,
        createdAt = ZonedDateTime
          .parse(source.getOrElse("createdAt", ZonedDateTime.now).toString)
          .toUTC,
        updatedAt = ZonedDateTime
          .parse(source.getOrElse("updatedAt", ZonedDateTime.now).toString)
          .toUTC,
        countVotesAgree = source.getOrElse("countVotesAgree", 0).asInstanceOf[Int],
        countVotesDisagree = source.getOrElse("countVotesDisagree", 0).asInstanceOf[Int],
        countVotesUnsure = source.getOrElse("countVotesUnsure", 0).asInstanceOf[Int],
        support = source.getOrElse("support", "NotFound").toString,
        context = source.getOrElse("context", "NotFound").toString,
        authorFirstName = source.getOrElse("authorFirstName", "NotFound").toString,
        authorPostalCode = source.getOrElse("authorPostalCode", "NotFound").toString,
        authorAge = source.getOrElse("authorAge", "NotFound").asInstanceOf[Int],
        themes = source
          .getOrElse("themes", Seq())
          .asInstanceOf[Seq[Map[String, String]]]
          .map(
            (t: Map[String, String]) => Theme(ThemeId(t.getOrElse("id", "NotFound")), t.getOrElse("label", "NotFound"))
          ),
        tags = source
          .getOrElse("tags", Seq())
          .asInstanceOf[Seq[Map[String, String]]]
          .map((t: Map[String, String]) => Tag(TagId(t.getOrElse("id", "NotFound")), t.getOrElse("label", "NoFound")))
      )
    )
  }

}
