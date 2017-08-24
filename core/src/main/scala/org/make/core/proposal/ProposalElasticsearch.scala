package org.make.core.proposal

import java.time.ZonedDateTime

import org.make.core.DateHelper._
import org.make.core.proposal.ProposalEvent.ProposalProposed
import org.make.core.tag.Tag
import org.make.core.theme.ThemeId
import org.make.core.user.UserId

object ProposalElasticsearchFieldNames {
  val id: String = "id"
  val userId: String = "userId"
  val content: String = "content"
  val slug: String = "slug"
  val status: String = "status"
  val createdAt: String = "createdAt"
  val updatedAt: String = "updatedAt"
  val countVotesAgree: String = "votesAgree.count"
  val countVotesDisagree: String = "votesDisagree.count"
  val countVotesUnsure: String = "votesUnsure.count"
  val operation: String = "operation"
  val source: String = "source"
  val location: String = "location"
  val question: String = "question"
  val trending: String = "trending"
  val label: String = "labels"
  val authorFirstName: String = "authorFirstName"
  val authorPostalCode: String = "authorPostalCode"
  val authorAge: String = "authorAge"
  val themeId: String = "themesId"
  val country: String = "country"
  val language: String = "language"
  val tagList: String = "tags"
  val tagId: String = "tags.id"
}

case class ProposalElasticsearch(id: ProposalId,
                                 userId: UserId,
                                 content: String,
                                 slug: String,
                                 status: String,
                                 createdAt: ZonedDateTime,
                                 updatedAt: Option[ZonedDateTime],
                                 votesAgree: Vote,
                                 votesDisagree: Vote,
                                 votesNeutral: Vote,
                                 operation: Option[String],
                                 source: Option[String],
                                 location: Option[String],
                                 question: Option[String],
                                 trending: Option[String],
                                 labels: Seq[String],
                                 authorFirstName: Option[String],
                                 authorPostalCode: Option[String],
                                 authorAge: Option[Int],
                                 country: String,
                                 language: String,
                                 themeId: Option[ThemeId],
                                 tags: Seq[Tag])

final case class Qualification(key: String, count: Int = 0, selected: Boolean = false)
final case class Vote(key: String, selected: Boolean = false, count: Int = 0, qualifications: Seq[Qualification])

object ProposalElasticsearch {

  val defaultCountry = "FR"
  val defaultLanguage = "fr"

  def apply(p: ProposalProposed): ProposalElasticsearch = {
    ProposalElasticsearch(
      id = p.id,
      userId = p.userId,
      content = p.content,
      slug = p.slug,
      status = "",
      createdAt = p.eventDate.toUTC,
      updatedAt = None,
      country = p.context.country.getOrElse(defaultCountry),
      language = p.context.language.getOrElse(defaultLanguage),
      votesAgree = Vote(
        key = "agree",
        qualifications =
          Seq(Qualification(key = "like-it"), Qualification(key = "doable"), Qualification(key = "platitude"))
      ),
      votesDisagree = Vote(
        key = "disagree",
        qualifications =
          Seq(Qualification(key = "no-way"), Qualification(key = "impossible"), Qualification(key = "platitude"))
      ),
      votesNeutral = Vote(
        key = "neutral",
        qualifications = Seq(
          Qualification(key = "doesnt-make-sense"),
          Qualification(key = "no-opinion"),
          Qualification(key = "do-not-care")
        )
      ),
      operation = p.context.operation,
      source = p.context.source,
      location = p.context.location,
      question = p.context.question,
      trending = None,
      labels = Seq(),
      authorFirstName = p.author.firstName,
      authorPostalCode = p.author.postalCode,
      authorAge = p.author.age,
      themeId = p.context.currentTheme,
      tags = Seq()
    )
  }

}
