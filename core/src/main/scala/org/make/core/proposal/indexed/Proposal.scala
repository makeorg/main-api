/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.core.proposal.indexed

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.CirceFormatters
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.reference.ThemeId
import org.make.core.tag.TagId
import org.make.core.user.UserId

object ProposalElasticsearchFieldNames {
  val id: String = "id"
  val userId: String = "userId"
  val content: String = "content"
  val contentFr: String = "content.fr"
  val contentFrStemmed: String = "content.stemmed-fr"
  val contentEn: String = "content.en"
  val contentEnStemmed: String = "content.stemmed-en"
  val contentIt: String = "content.it"
  val contentItStemmed: String = "content.stemmed-it"
  val contentGeneral: String = "content.general"
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
  val organisations: String = "organisations"
  val organisationId: String = "organisations.organisationId"
  val organisationName: String = "organisations.organisationName"
  val scores: String = "scores"
}

case class IndexedProposal(id: ProposalId,
                           userId: UserId,
                           content: String,
                           slug: String,
                           status: ProposalStatus,
                           createdAt: ZonedDateTime,
                           updatedAt: Option[ZonedDateTime],
                           votes: Seq[IndexedVote],
                           scores: IndexedScores,
                           context: Option[Context],
                           trending: Option[String],
                           labels: Seq[String],
                           author: Author,
                           organisations: Seq[IndexedOrganisationInfo],
                           country: String,
                           language: String,
                           themeId: Option[ThemeId],
                           tags: Seq[IndexedTag],
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

final case class Author(firstName: Option[String],
                        organisationName: Option[String],
                        postalCode: Option[String],
                        age: Option[Int],
                        avatarUrl: Option[String])

object Author {
  implicit val encoder: ObjectEncoder[Author] = deriveEncoder[Author]
  implicit val decoder: Decoder[Author] = deriveDecoder[Author]
}

final case class IndexedOrganisationInfo(organisationId: UserId, organisationName: Option[String])

object IndexedOrganisationInfo {
  implicit val encoder: ObjectEncoder[IndexedOrganisationInfo] = deriveEncoder[IndexedOrganisationInfo]
  implicit val decoder: Decoder[IndexedOrganisationInfo] = deriveDecoder[IndexedOrganisationInfo]

  def apply(organisationInfo: OrganisationInfo): IndexedOrganisationInfo =
    IndexedOrganisationInfo(
      organisationId = organisationInfo.organisationId,
      organisationName = organisationInfo.organisationName
    )
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

final case class IndexedScores(boost: Double = 0,
                               engagement: Double,
                               adhesion: Double,
                               realistic: Double,
                               topScore: Double,
                               controversy: Double,
                               rejection: Double)

object IndexedScores {
  implicit val encoder: ObjectEncoder[IndexedScores] = deriveEncoder[IndexedScores]
  implicit val decoder: Decoder[IndexedScores] = deriveDecoder[IndexedScores]

  def empty: IndexedScores = IndexedScores(0, 0, 0, 0, 0, 0, 0)
}

final case class ProposalsSearchResult(total: Long, results: Seq[IndexedProposal])

object ProposalsSearchResult {
  implicit val encoder: ObjectEncoder[ProposalsSearchResult] = deriveEncoder[ProposalsSearchResult]
  implicit val decoder: Decoder[ProposalsSearchResult] = deriveDecoder[ProposalsSearchResult]

  def empty: ProposalsSearchResult = ProposalsSearchResult(0, Seq.empty)
}

final case class IndexedTag(tagId: TagId, label: String, display: Boolean)

object IndexedTag {
  implicit val encoder: ObjectEncoder[IndexedTag] = deriveEncoder[IndexedTag]
  implicit val decoder: Decoder[IndexedTag] = deriveDecoder[IndexedTag]
}
