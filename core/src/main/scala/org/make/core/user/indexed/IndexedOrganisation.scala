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

package org.make.core.user.indexed

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{BusinessConfig, CirceFormatters, SlugHelper}

object OrganisationElasticsearchFieldNames {
  val organisationId: String = "organisationId"
  val organisationName: String = "organisationName"
  val organisationNameKeyword: String = "organisationName.keyword"
  val organisationNameGeneral: String = "organisationName.general"
  val slug: String = "slug"
  val avatarUrl: String = "avatarUrl"
  val description: String = "description"
  val canBeFollowed: String = "canBeFollowed"
  val proposalsCount: String = "proposalsCount"
  val votesCount: String = "votesCount"
  val language: String = "language"
  val country: String = "country"
  val countsByQuestion: String = "countsByQuestion"
  val countsByQuestionQuestionId: String = "countsByQuestion.questionId"

  def organisationNameLanguageSubfield(language: Language, stemmed: Boolean = false): Option[String] = {
    BusinessConfig.supportedCountries
      .find(_.supportedLanguages.contains(language))
      .map { _ =>
        if (stemmed)
          s"organisationName.$language-stemmed"
        else
          s"organisationName.$language"
      }
  }
}

final case class IndexedOrganisation(
  organisationId: UserId,
  organisationName: Option[String],
  slug: Option[String],
  avatarUrl: Option[String],
  description: Option[String],
  publicProfile: Boolean,
  proposalsCount: Int,
  votesCount: Int,
  country: Country,
  website: Option[String],
  countsByQuestion: Seq[ProposalsAndVotesCountsByQuestion]
)

object IndexedOrganisation extends CirceFormatters {
  implicit val encoder: Encoder[IndexedOrganisation] = deriveEncoder[IndexedOrganisation]
  implicit val decoder: Decoder[IndexedOrganisation] = deriveDecoder[IndexedOrganisation]

  def createFromOrganisation(
    organisation: User,
    countsByQuestion: Seq[ProposalsAndVotesCountsByQuestion] = Seq.empty
  ): IndexedOrganisation = {
    IndexedOrganisation(
      organisationId = organisation.userId,
      organisationName = organisation.organisationName,
      slug = organisation.organisationName.map(SlugHelper.apply),
      avatarUrl = organisation.profile.flatMap(_.avatarUrl),
      description = organisation.profile.flatMap(_.description),
      publicProfile = organisation.publicProfile,
      proposalsCount = countsByQuestion.map(_.proposalsCount).sum,
      votesCount = countsByQuestion.map(_.votesCount).sum,
      country = organisation.country,
      website = organisation.profile.flatMap(_.website),
      countsByQuestion = countsByQuestion
    )
  }
}

final case class OrganisationSearchResult(total: Long, results: Seq[IndexedOrganisation])

object OrganisationSearchResult {
  implicit val encoder: Encoder[OrganisationSearchResult] = deriveEncoder[OrganisationSearchResult]
  implicit val decoder: Decoder[OrganisationSearchResult] = deriveDecoder[OrganisationSearchResult]

  def empty: OrganisationSearchResult = OrganisationSearchResult(0, Seq.empty)
}

final case class ProposalsAndVotesCountsByQuestion(questionId: QuestionId, proposalsCount: Int, votesCount: Int)

object ProposalsAndVotesCountsByQuestion {
  implicit val encoder: Encoder[ProposalsAndVotesCountsByQuestion] = deriveEncoder[ProposalsAndVotesCountsByQuestion]
  implicit val decoder: Decoder[ProposalsAndVotesCountsByQuestion] = deriveDecoder[ProposalsAndVotesCountsByQuestion]
}
