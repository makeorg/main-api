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
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{BusinessConfig, CirceFormatters, SlugHelper}

import scala.annotation.meta.field

object OrganisationElasticsearchFieldNames {
  val organisationId = "organisationId"
  val organisationName = "organisationName"
  val organisationNameKeyword = "organisationName.keyword"
  val organisationNameGeneral = "organisationName.general"
  val slug = "slug"
  val avatarUrl = "avatarUrl"
  val description = "description"
  val canBeFollowed = "canBeFollowed"
  val proposalsCount = "proposalsCount"
  val votesCount = "votesCount"
  val language = "language"
  val country = "country"

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

case class IndexedOrganisation(
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55")
  organisationId: UserId,
  organisationName: Option[String],
  slug: Option[String],
  avatarUrl: Option[String],
  description: Option[String],
  publicProfile: Boolean,
  @(ApiModelProperty @field)(dataType = "int", example = "42")
  proposalsCount: Option[Int],
  @(ApiModelProperty @field)(dataType = "int", example = "42")
  votesCount: Option[Int],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "FR")
  country: Country
)

object IndexedOrganisation extends CirceFormatters {
  implicit val encoder: ObjectEncoder[IndexedOrganisation] = deriveEncoder[IndexedOrganisation]
  implicit val decoder: Decoder[IndexedOrganisation] = deriveDecoder[IndexedOrganisation]

  def createFromOrganisation(organisation: User,
                             proposalsCount: Option[Int] = None,
                             votesCount: Option[Int] = None): IndexedOrganisation = {
    IndexedOrganisation(
      organisationId = organisation.userId,
      organisationName = organisation.organisationName,
      slug = organisation.organisationName.map(SlugHelper.apply),
      avatarUrl = organisation.profile.flatMap(_.avatarUrl),
      description = organisation.profile.flatMap(_.description),
      publicProfile = organisation.publicProfile,
      proposalsCount = proposalsCount,
      votesCount = votesCount,
      language = organisation.language,
      country = organisation.country
    )
  }
}

final case class OrganisationSearchResult(total: Long, results: Seq[IndexedOrganisation])

object OrganisationSearchResult {
  implicit val encoder: ObjectEncoder[OrganisationSearchResult] = deriveEncoder[OrganisationSearchResult]
  implicit val decoder: Decoder[OrganisationSearchResult] = deriveDecoder[OrganisationSearchResult]

  def empty: OrganisationSearchResult = OrganisationSearchResult(0, Seq.empty)
}
