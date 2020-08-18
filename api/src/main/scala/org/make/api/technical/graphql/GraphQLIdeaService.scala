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
import org.make.api.idea.IdeaServiceComponent
import org.make.core.idea.IdeaId
import zio.query.DataSource

import scala.concurrent.{ExecutionContext, Future}

trait GraphQLIdeaServiceComponent {
  def ideaDataSource: DataSource[Any, GetIdea]
}

trait DefaultGraphQLIdeaServiceComponent extends GraphQLIdeaServiceComponent {
  this: IdeaServiceComponent =>

  override val ideaDataSource: DataSource[Any, GetIdea] = {
    def findFromIds(ideaIds: Seq[IdeaId])(ec: ExecutionContext): Future[Map[IdeaId, GraphQLIdea]] = {
      ideaService
        .fetchAllByIdeaIds(ideaIds)
        .map(_.map(idea => idea.ideaId -> GraphQLIdea(idea.ideaId, idea.name)).toMap)(ec)
    }

    DataSourceHelper.one("idea-datasource", findFromIds)
  }

}

final case class GetIdea(ids: IdeaId) extends IdsRequest[Id, IdeaId, GraphQLIdea]
final case class GraphQLIdea(ideaId: IdeaId, name: String)
