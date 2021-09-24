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

package org.make.api.tag

import org.make.core.Order
import org.make.core.question.QuestionId
import org.make.core.tag.{Tag, TagId}

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentTagServiceComponent {
  def persistentTagService: PersistentTagService
}

trait PersistentTagService {
  def get(tagId: TagId): Future[Option[Tag]]
  def findAll(): Future[Seq[Tag]]
  def findAllFromIds(tagsIds: Seq[TagId]): Future[Seq[Tag]]
  def findAllDisplayed(): Future[Seq[Tag]]
  def findByLabel(label: String): Future[Seq[Tag]]
  def findByLabelLike(partialLabel: String): Future[Seq[Tag]]
  def findByQuestion(questionId: QuestionId): Future[Seq[Tag]]
  def persist(tag: Tag): Future[Tag]
  def update(tag: Tag): Future[Option[Tag]]
  def remove(tagId: TagId): Future[Int]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    onlyDisplayed: Boolean,
    persistentTagFilter: PersistentTagFilter
  ): Future[Seq[Tag]]
  def count(persistentTagFilter: PersistentTagFilter): Future[Int]
}
