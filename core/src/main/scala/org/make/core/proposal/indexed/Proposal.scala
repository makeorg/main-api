package org.make.core.proposal.indexed

import java.time.ZonedDateTime

import org.make.core.proposal._
import org.make.core.reference.{Tag, ThemeId}
import org.make.core.user.UserId

import scala.concurrent.Future

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
  val contextOperation: String = "context.operation"
  val contextSource: String = "context.source"
  val contextLocation: String = "context.location"
  val contextQuestion: String = "context.question"
  val trending: String = "trending"
  val labels: String = "labels"
  val authorFirstName: String = "author.firstName"
  val authorPostalCode: String = "author.postalCode"
  val authorAge: String = "author.age"
  val themeId: String = "themeId"
  val country: String = "country"
  val language: String = "language"
  val tags: String = "tags"
  val tagId: String = "tags.id"
}

case class ProposalsResult(total: Future[Int], results: Future[Seq[IndexedProposal]])

case class IndexedProposal(id: ProposalId,
                           userId: UserId,
                           content: String,
                           slug: String,
                           status: ProposalStatus,
                           createdAt: ZonedDateTime,
                           updatedAt: Option[ZonedDateTime],
                           votes: Seq[IndexedVote],
                           context: Option[Context],
                           trending: Option[String],
                           labels: Seq[String],
                           author: Author,
                           country: String,
                           language: String,
                           themeId: Option[ThemeId],
                           tags: Seq[Tag])

final case class Context(operation: Option[String],
                         source: Option[String],
                         location: Option[String],
                         question: Option[String])
final case class Author(firstName: Option[String], postalCode: Option[String], age: Option[Int])
