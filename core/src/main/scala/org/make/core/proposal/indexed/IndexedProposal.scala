package org.make.core.proposal.indexed

import java.time.ZonedDateTime

import org.make.core.proposal._
import org.make.core.reference.{Tag, ThemeId}
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
                           status: ProposalStatus,
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

final case class ProposalContext(operation: Option[String],
                                 source: Option[String],
                                 location: Option[String],
                                 question: Option[String])
final case class Author(firstName: Option[String], postalCode: Option[String], age: Option[Int])
