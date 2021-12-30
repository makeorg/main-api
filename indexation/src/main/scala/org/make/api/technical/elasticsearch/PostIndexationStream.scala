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

package org.make.api.technical.elasticsearch

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import org.make.api.post.PostSearchEngineComponent
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.post.Post
import org.make.core.post.indexed.IndexedPost

import scala.concurrent.Future

trait PostIndexationStream extends IndexationStream with PostSearchEngineComponent with Logging {

  object PostStream {

    def flowIndexPosts(postIndexName: String): Flow[Post, IndexationStatus, NotUsed] =
      grouped[Post].via(runIndexPost(postIndexName))

    def runIndexPost(postIndexName: String): Flow[Seq[Post], IndexationStatus, NotUsed] = {
      Flow[Seq[Post]].mapAsync(singleAsync)(posts => executeIndexPosts(posts, postIndexName))
    }

    private def executeIndexPosts(posts: Seq[Post], postIndexName: String): Future[IndexationStatus] = {
      elasticsearchPostAPI
        .indexPosts(posts.map(post => IndexedPost.createFromPost(post)), Some(Index(postIndexName)))
    }

  }
}
