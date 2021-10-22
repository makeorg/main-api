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

import org.make.api.proposal._
import org.make.core.common.indexed.Sort
import org.make.core.proposal.{CountrySearchFilter => _, LanguageSearchFilter => _, _}
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{Order, RequestContext}

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait OrganisationServiceComponent {
  def organisationService: OrganisationService
}

trait OrganisationService {
  def getOrganisation(id: UserId): Future[Option[User]]
  def getOrganisations: Future[Seq[User]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    ids: Option[Seq[UserId]],
    organisationName: Option[String]
  ): Future[Seq[User]]
  def count(ids: Option[Seq[UserId]], organisationName: Option[String]): Future[Int]
  def search(
    organisationName: Option[String],
    slug: Option[String],
    organisationIds: Option[Seq[UserId]],
    country: Option[Country],
    language: Option[Language]
  ): Future[OrganisationSearchResult]
  def searchWithQuery(query: OrganisationSearchQuery): Future[OrganisationSearchResult]
  def register(organisationRegisterData: OrganisationRegisterData, requestContext: RequestContext): Future[User]
  def update(
    organisation: User,
    moderatorId: Option[UserId],
    oldEmail: String,
    requestContext: RequestContext
  ): Future[UserId]
  def getVotedProposals(
    organisationId: UserId,
    maybeUserId: Option[UserId],
    filterVotes: Option[Seq[VoteKey]],
    filterQualifications: Option[Seq[QualificationKey]],
    sort: Option[Sort],
    limit: Option[Int],
    skip: Option[Int],
    requestContext: RequestContext
  ): Future[ProposalsResultWithUserVoteSeededResponse]
}

final case class OrganisationRegisterData(
  name: String,
  email: String,
  password: Option[String],
  avatar: Option[String],
  description: Option[String],
  country: Country,
  website: Option[String]
)
