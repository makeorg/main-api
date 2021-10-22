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

package org.make.api.idea.topIdeaComments

import org.make.core.idea._
import org.make.core.user.UserId

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentTopIdeaCommentServiceComponent {
  def persistentTopIdeaCommentService: PersistentTopIdeaCommentService
}

trait PersistentTopIdeaCommentService {
  def getById(topIdeaCommentId: TopIdeaCommentId): Future[Option[TopIdeaComment]]
  def persist(topIdeaComment: TopIdeaComment): Future[TopIdeaComment]
  def modify(topIdeaComment: TopIdeaComment): Future[TopIdeaComment]
  def remove(topIdeaCommentId: TopIdeaCommentId): Future[Unit]
  def search(
    start: Start,
    end: Option[End],
    topIdeaIds: Option[Seq[TopIdeaId]],
    personalityIds: Option[Seq[UserId]]
  ): Future[Seq[TopIdeaComment]]
  def count(topIdeaIds: Option[Seq[TopIdeaId]], personalityIds: Option[Seq[UserId]]): Future[Int]
  def countForAll(topIdeaIds: Seq[TopIdeaId]): Future[Map[String, Int]]
}
