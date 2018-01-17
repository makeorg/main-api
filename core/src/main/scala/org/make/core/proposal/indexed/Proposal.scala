package org.make.core.proposal.indexed

import java.time.ZonedDateTime

import io.circe.{Decoder, ObjectEncoder}
import io.circe.generic.semiauto._
import org.make.core.CirceFormatters
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.reference.{Tag, ThemeId}
import org.make.core.user.UserId

object ProposalElasticsearchFieldNames {
  val id: String = "id"
  val userId: String = "userId"
  val content: String = "content"
  val contentStemmed: String = "content.stemmed"
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
  val tagId: String = "tags.tagId"
  val ideaId: String = "ideaId"
  val operationId: String = "operationId"
}

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
                           tags: Seq[Tag],
                           ideaId: Option[IdeaId],
                           operationId: Option[OperationId])

object IndexedProposal extends CirceFormatters {
  implicit val encoder: ObjectEncoder[IndexedProposal] = deriveEncoder[IndexedProposal]
  implicit val decoder: Decoder[IndexedProposal] = deriveDecoder[IndexedProposal]
}

final case class Context(operation: Option[OperationId],
                         source: Option[String],
                         location: Option[String],
                         question: Option[String])

object Context {
  implicit val encoder: ObjectEncoder[Context] = deriveEncoder[Context]
  implicit val decoder: Decoder[Context] = deriveDecoder[Context]
}

final case class Author(firstName: Option[String], postalCode: Option[String], age: Option[Int])

object Author {
  implicit val encoder: ObjectEncoder[Author] = deriveEncoder[Author]
  implicit val decoder: Decoder[Author] = deriveDecoder[Author]
}

final case class IndexedVote(key: VoteKey, count: Int = 0, qualifications: Seq[IndexedQualification])

object IndexedVote {
  implicit val encoder: ObjectEncoder[IndexedVote] = deriveEncoder[IndexedVote]
  implicit val decoder: Decoder[IndexedVote] = deriveDecoder[IndexedVote]

  def apply(vote: Vote): IndexedVote =
    IndexedVote(
      key = vote.key,
      count = vote.count,
      qualifications = vote.qualifications.map(IndexedQualification.apply)
    )
}

final case class IndexedQualification(key: QualificationKey, count: Int = 0)

object IndexedQualification {
  implicit val encoder: ObjectEncoder[IndexedQualification] = deriveEncoder[IndexedQualification]
  implicit val decoder: Decoder[IndexedQualification] = deriveDecoder[IndexedQualification]

  def apply(qualification: Qualification): IndexedQualification =
    IndexedQualification(key = qualification.key, count = qualification.count)
}

final case class ProposalsSearchResult(total: Int, results: Seq[IndexedProposal])

object ProposalsSearchResult {
  implicit val encoder: ObjectEncoder[ProposalsSearchResult] = deriveEncoder[ProposalsSearchResult]

  def empty: ProposalsSearchResult = ProposalsSearchResult(0, Seq.empty)
}
