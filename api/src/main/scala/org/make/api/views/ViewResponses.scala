/*
 *  Make.org Core API
 *  Copyright (C) 2019 Make.org
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

package org.make.api.views

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.make.api.organisation.OrganisationsSearchResultResponse
import org.make.api.proposal.{ProposalsResultSeededResponse}
import org.make.core.operation.indexed.OperationOfQuestionSearchResult

final case class SearchViewResponse(
  proposals: ProposalsResultSeededResponse,
  questions: OperationOfQuestionSearchResult,
  organisations: OrganisationsSearchResultResponse
)

object SearchViewResponse {
  implicit val encoder: Encoder[SearchViewResponse] = deriveEncoder[SearchViewResponse]
  implicit val decoder: Decoder[SearchViewResponse] = deriveDecoder[SearchViewResponse]
}

final case class AvailableCountry(countryCode: String, activeConsultations: Boolean)

object AvailableCountry {
  implicit val decoder: Decoder[AvailableCountry] = deriveDecoder
  implicit val encoder: Encoder[AvailableCountry] = deriveEncoder
}
