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

package org.make.api.post

import org.make.core.post.indexed.{PostSearchQuery, PostSearchResult}
import org.make.core.post.Post

import scala.concurrent.Future

trait PostService {
  def search(searchQuery: PostSearchQuery): Future[PostSearchResult]
  def fetchPostsForHome(): Future[Seq[Post]]
}

trait PostServiceComponent {
  def postService: PostService
}
