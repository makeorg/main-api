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

package org.make.api.keyword

import org.make.api.technical.IdGeneratorComponent
import org.make.core.question.QuestionId
import org.make.core.keyword._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait KeywordServiceComponent {
  def keywordService: KeywordService
}

trait KeywordService {
  def get(key: String, questionId: QuestionId): Future[Option[Keyword]]
  def findTop(questionId: QuestionId, limit: Int): Future[Seq[Keyword]]
  def addAndReplaceTop(questionId: QuestionId, keywords: Seq[Keyword]): Future[Unit]
}

trait DefaultKeywordServiceComponent extends KeywordServiceComponent {
  this: PersistentKeywordServiceComponent with IdGeneratorComponent =>

  override lazy val keywordService: KeywordService = new DefaultKeywordService

  class DefaultKeywordService extends KeywordService {

    override def get(key: String, questionId: QuestionId): Future[Option[Keyword]] = {
      persistentKeywordService.get(key, questionId)
    }

    override def findTop(questionId: QuestionId, limit: Int): Future[Seq[Keyword]] = {
      persistentKeywordService.findTop(questionId, limit)
    }

    override def addAndReplaceTop(questionId: QuestionId, keywords: Seq[Keyword]): Future[Unit] = {
      for {
        _           <- persistentKeywordService.resetTop(questionId)
        allKeywords <- persistentKeywordService.findAll(questionId)
        (existingKeywords, newKeywords) = keywords.partition(kw => allKeywords.exists(_.key == kw.key))
        _ <- persistentKeywordService.updateTop(questionId, existingKeywords)
        _ <- persistentKeywordService.createKeywords(questionId, newKeywords)
      } yield {}
    }
  }
}
