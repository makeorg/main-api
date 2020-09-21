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

import cats.Id
import org.make.api.user.UserServiceComponent
import org.make.core.user.{UserId, UserType}
import zio.query.DataSource

import scala.concurrent.{ExecutionContext, Future}

trait GraphQLAuthorServiceComponent {
  def authorDataSource: DataSource[Any, GetAuthor]
}

trait DefaultGraphQLAuthorServiceComponent extends GraphQLAuthorServiceComponent {
  this: UserServiceComponent =>

  override val authorDataSource: DataSource[Any, GetAuthor] = {
    def findFromIds(userIds: Seq[UserId])(ec: ExecutionContext): Future[Map[UserId, GraphQLAuthor]] =
      userService
        .getUsersByUserIds(userIds)
        .map(
          _.map(
            user =>
              user.userId -> GraphQLAuthor(
                user.userId,
                user.firstName,
                user.profile.flatMap(_.avatarUrl),
                user.userType
              )
          ).toMap
        )(ec)

    DataSourceHelper.one("user-datasource", findFromIds)
  }
}

final case class GetAuthor(ids: UserId) extends IdsRequest[Id, UserId, GraphQLAuthor]

final case class GraphQLAuthor(id: UserId, firstName: Option[String], avatar: Option[String], userType: UserType)
