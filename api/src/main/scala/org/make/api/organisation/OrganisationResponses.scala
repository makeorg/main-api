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

package org.make.api.organisation

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations.ApiModelProperty
import org.make.core.CirceFormatters
import org.make.core.reference.{Country, Language}
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.user.UserId

import scala.annotation.meta.field

case class OrganisationSearchResponse(
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
  country: Country,
  website: Option[String]
)

object OrganisationSearchResponse extends CirceFormatters {
  implicit val encoder: Encoder[OrganisationSearchResponse] = deriveEncoder[OrganisationSearchResponse]
  implicit val decoder: Decoder[OrganisationSearchResponse] = deriveDecoder[OrganisationSearchResponse]

  def fromIndexedOrganisation(organisation: IndexedOrganisation): OrganisationSearchResponse = {
    OrganisationSearchResponse(
      organisationId = organisation.organisationId,
      organisationName = organisation.organisationName,
      slug = organisation.slug,
      avatarUrl = organisation.avatarUrl,
      description = organisation.description,
      publicProfile = organisation.publicProfile,
      proposalsCount = organisation.proposalsCount,
      votesCount = organisation.votesCount,
      language = organisation.language,
      country = organisation.country,
      website = organisation.website
    )
  }
}

final case class OrganisationsSearchResultResponse(total: Long, results: Seq[OrganisationSearchResponse])

object OrganisationsSearchResultResponse {
  implicit val encoder: Encoder[OrganisationsSearchResultResponse] = deriveEncoder[OrganisationsSearchResultResponse]
  implicit val decoder: Decoder[OrganisationsSearchResultResponse] = deriveDecoder[OrganisationsSearchResultResponse]

  def empty: OrganisationsSearchResultResponse = OrganisationsSearchResultResponse(0, Seq.empty)

  def fromOrganisationSearchResult(results: OrganisationSearchResult): OrganisationsSearchResultResponse =
    OrganisationsSearchResultResponse(
      total = results.total,
      results = results.results.map(OrganisationSearchResponse.fromIndexedOrganisation)
    )
}
