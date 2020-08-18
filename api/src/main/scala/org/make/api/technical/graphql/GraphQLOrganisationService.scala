/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.graphql

import org.make.api.organisation.OrganisationServiceComponent
import org.make.core.user.UserId
import zio.query.DataSource

import scala.concurrent.{ExecutionContext, Future}

trait GraphQLOrganisationServiceComponent {
  def organisationDataSource: DataSource[Any, GetOrganisations]
}

trait DefaultGraphQLOrganisationServiceComponent extends GraphQLOrganisationServiceComponent {
  this: OrganisationServiceComponent =>

  override val organisationDataSource: DataSource[Any, GetOrganisations] = {
    def findFromIds(organisationsIds: Seq[UserId])(ec: ExecutionContext): Future[Map[UserId, GraphQLOrganisation]] =
      organisationService
        .search(None, None, Some(organisationsIds), None, None)
        .map(
          _.results
            .map(
              orga => orga.organisationId -> GraphQLOrganisation(orga.organisationId, orga.organisationName, orga.slug)
            )
            .toMap
        )(ec)

    DataSourceHelper.seq("organisation-datasource", findFromIds)
  }

}

final case class GetOrganisations(ids: List[UserId]) extends IdsRequest[List, UserId, GraphQLOrganisation]
final case class GraphQLOrganisation(
  organisationId: UserId,
  organisationName: Option[String],
  organisationSlug: Option[String]
)
