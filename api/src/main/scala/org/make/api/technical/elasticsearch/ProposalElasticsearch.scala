package org.make.api.technical.elasticsearch

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.SearchHit
import com.typesafe.scalalogging.StrictLogging
import org.make.api.Predef._
import org.make.core.proposal.ProposalEvent.ProposalProposed
import org.make.core.tag.{Tag, TagId}
import org.make.core.theme.{Theme, ThemeId}

object ProposalElasticsearchFieldNames {
  val id: String = "id"
  val userId: String = "userId"
  val content: String = "content"
  val slug: String = "slug"
  val createdAt: String = "createdAt"
  val updatedAt: String = "updatedAt"
  val countVotesAgree: String = "countVotesAgree"
  val countVotesDisagree: String = "countVotesDisagree"
  val countVotesUnsure: String = "countVotesUnsure"
  val support: String = "support"
  val context: String = "context"
  val authorFirstName: String = "authorFirstName"
  val authorPostalCode: String = "authorPostalCode"
  val authorAge: String = "authorAge"
  val themeList: String = "themes"
  val themeId: String = "themes.id"
  val tagList: String = "tags"
  val tagId: String = "tags.id"
}

object ProposalElasticsearchDate {
  // TODO add hour and zone in format
  val format: String = "yyyy-MM-dd"
  val formatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .appendPattern(format)
    .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
    .toFormatter
    .withZone(ZoneId.of("Europe/Berlin"))
}

case class ProposalElasticsearch(id: UUID,
                                 userId: Option[UUID],
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
        userId = Some(UUID.fromString(p.userId.value)),
        content = p.content,
        slug = "",
        createdAt = p.eventDate.toUTC,
        updatedAt = p.eventDate.toUTC,
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
        userId = Some(UUID.fromString(source.getOrElse("userId", "NotFound").toString)),
        content = source.getOrElse("content", "NotFound").toString,
        slug = source.getOrElse("slug", "NotFound").toString,
        createdAt = ZonedDateTime
          .parse(source.getOrElse("createdAt", ZonedDateTime.now).toString, ProposalElasticsearchDate.formatter)
          .toUTC,
        updatedAt = ZonedDateTime
          .parse(source.getOrElse("updatedAt", ZonedDateTime.now).toString, ProposalElasticsearchDate.formatter)
          .toUTC,
        countVotesAgree = source.getOrElse("countVotesAgree", 0).asInstanceOf[Int],
        countVotesDisagree = source.getOrElse("countVotesDisagree", 0).asInstanceOf[Int],
        countVotesUnsure = source.getOrElse("countVotesUnsure", 0).asInstanceOf[Int],
        support = source.getOrElse("support", "NotFound").toString,
        context = source.getOrElse("context", "NotFound").toString,
        authorFirstName = source.getOrElse("authorFirstName", "NotFound").toString,
        authorPostalCode = source.getOrElse("authorPostalCode", "NotFound").toString,
        authorAge = source.getOrElse("authorAge", 0).asInstanceOf[Int],
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
