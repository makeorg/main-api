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
  val proposalContextOperation: String = "proposalContext.operation"
  val proposalContextSource: String = "proposalContext.source"
  val proposalContextLocation: String = "proposalContext.location"
  val proposalContextQuestion: String = "proposalContext.question"
  val trending: String = "trending"
  val label: String = "labels"
  val authorFirstName: String = "author.firstName"
  val authorPostalCode: String = "author.postalCode"
  val authorAge: String = "author.age"
  val themeId: String = "themesId"
  val country: String = "country"
  val language: String = "language"
  val tagList: String = "tags"
  val tagId: String = "tags.id"
}

case class IndexedProposal(id: ProposalId,
                           userId: UserId,
                           content: String,
                           slug: String,
                           status: String,
                           createdAt: ZonedDateTime,
                           updatedAt: Option[ZonedDateTime],
                           votesAgree: Vote,
                           votesDisagree: Vote,
                           votesNeutral: Vote,
                           proposalContext: ProposalContext,
                           trending: Option[String],
                           labels: Seq[String],
                           author: Author,
                           country: String,
                           language: String,
                           themeId: Option[ThemeId],
                           tags: Seq[Tag])

final case class Qualification(key: String, count: Int = 0, selected: Boolean = false)
final case class Vote(key: String, selected: Boolean = false, count: Int = 0, qualifications: Seq[Qualification])
final case class ProposalContext(operation: Option[String],
                                 source: Option[String],
                                 location: Option[String],
                                 question: Option[String])
final case class Author(firstName: Option[String], postalCode: Option[String], age: Option[Int])

//TODO: use enums instead of strings for vote / qualifs
object IndexedProposal {

  val defaultCountry = "FR"
  val defaultLanguage = "fr"

  def apply(p: ProposalProposed): IndexedProposal = {
    IndexedProposal(
      id = p.id,
      userId = p.userId,
      content = p.content,
      slug = p.slug,
      status = Pending.shortName,
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
      proposalContext = ProposalContext(
        operation = p.context.operation,
        source = p.context.source,
        location = p.context.location,
        question = p.context.question
      ),
      trending = None,
      labels = Seq(),
      author = Author(firstName = p.author.firstName, postalCode = p.author.postalCode, age = p.author.age),
      themeId = p.context.currentTheme,
      tags = Seq()
    )
  }

}
