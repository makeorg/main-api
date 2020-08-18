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

import org.make.api.tag.TagServiceComponent
import org.make.api.tagtype.TagTypeServiceComponent
import org.make.core.proposal.indexed.IndexedTag
import org.make.core.tag.TagId
import zio.query.DataSource

import scala.concurrent.{ExecutionContext, Future}

trait GraphQLTagServiceComponent {
  def tagDataSource: DataSource[Any, GetTags]
}

trait DefaultGraphQLTagServiceComponent extends GraphQLTagServiceComponent {
  this: TagServiceComponent with TagTypeServiceComponent =>

  override val tagDataSource: DataSource[Any, GetTags] = {
    def findAllFromIds(tagsIds: Seq[TagId])(executionContext: ExecutionContext): Future[Map[TagId, GraphQLTag]] = {
      implicit val ec: ExecutionContext = executionContext
      for {
        tags     <- tagService.findByTagIds(tagsIds)
        tagTypes <- tagTypeService.findAll()
      } yield tagService.retrieveIndexedTags(tags, tagTypes).map(tag => tag.tagId -> GraphQLTag.fromTag(tag)).toMap
    }

    DataSourceHelper.seq("tag-datasource", findAllFromIds)
  }
}

final case class GetTags(ids: List[TagId]) extends IdsRequest[List, TagId, GraphQLTag]

final case class GraphQLTag(tagId: TagId, label: String, display: Boolean)

object GraphQLTag {
  def fromTag(tag: IndexedTag): GraphQLTag = GraphQLTag(tag.tagId, tag.label, tag.display)
}
