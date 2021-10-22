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

import org.make.core.proposal.indexed.IndexedTag
import org.make.core.question.{Question, QuestionId}
import org.make.core.tag._
import org.make.core.{Order, RequestContext}

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait TagServiceComponent {
  def tagService: TagService
}

final case class TagFilter(
  tagIds: Option[Seq[TagId]] = None,
  label: Option[String] = None,
  tagTypeId: Option[TagTypeId] = None,
  questionIds: Option[Seq[QuestionId]] = None
)

object TagFilter {
  val empty: TagFilter = TagFilter()
}

trait TagService {
  def getTag(tagId: TagId): Future[Option[Tag]]
  def createTag(
    label: String,
    tagTypeId: TagTypeId,
    question: Question,
    display: TagDisplay = TagDisplay.Inherit,
    weight: Float = 0f
  ): Future[Tag]
  def findAll(): Future[Seq[Tag]]
  def findAllDisplayed(): Future[Seq[Tag]]
  def findByTagIds(tagIds: Seq[TagId]): Future[Seq[Tag]]
  def findByQuestionId(questionId: QuestionId): Future[Seq[Tag]]
  def findByLabel(partialLabel: String, like: Boolean): Future[Seq[Tag]]
  def updateTag(
    tagId: TagId,
    label: String,
    display: TagDisplay,
    tagTypeId: TagTypeId,
    weight: Float,
    question: Question,
    requestContext: RequestContext = RequestContext.empty
  ): Future[Option[Tag]]
  def retrieveIndexedTags(tags: Seq[Tag], tagTypes: Seq[TagType]): Seq[IndexedTag]
  def find(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    onlyDisplayed: Boolean = false,
    tagFilter: TagFilter = TagFilter.empty
  ): Future[Seq[Tag]]
  def count(tagFilter: TagFilter = TagFilter.empty): Future[Int]
}
