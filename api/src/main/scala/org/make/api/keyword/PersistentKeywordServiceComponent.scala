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

import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ScalikeSupport._
import org.make.api.technical.ShortenedNames
import org.make.core.keyword.Keyword
import org.make.core.question.QuestionId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentKeywordServiceComponent {
  def persistentKeywordService: PersistentKeywordService
}

trait PersistentKeywordService {
  def findAll(questionId: QuestionId, limit: Int): Future[Seq[Keyword]]
  def replaceAll(questionId: QuestionId, keywords: Seq[Keyword]): Future[Seq[Keyword]]
}

trait DefaultPersistentKeywordServiceComponent extends PersistentKeywordServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentKeywordService: PersistentKeywordService = new DefaultPersistentKeywordService

  class DefaultPersistentKeywordService
      extends PersistentKeywordService
      with ShortenedNames
      with SQLSyntaxSupport[Keyword]
      with Logging {
    private val keywords = SQLSyntaxSupportFactory[Keyword]()
    private val kw = keywords.syntax

    override def findAll(questionId: QuestionId, limit: Int): Future[Seq[Keyword]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(keywords.as(kw))
            .where(sqls.eq(kw.questionId, questionId))
            .orderBy(kw.score)
            .desc
            .limit(limit)
        }.map(keywords.apply(kw.resultName)).list().apply()
      })
    }

    override def replaceAll(questionId: QuestionId, items: Seq[Keyword]): Future[Seq[Keyword]] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        Seq(deleteAll(questionId), insertAll(items))
      }).map(_ => items)
    }

    private def deleteAll(questionId: QuestionId)(implicit session: DBSession): Boolean = {
      withSQL {
        delete
          .from(keywords.as(kw))
          .where(sqls.eq(kw.questionId, questionId))
      }.update().apply() >= 0
    }

    private def insertAll(items: Seq[Keyword])(implicit session: DBSession): Boolean = {
      def values(keyword: Keyword, columns: Seq[SQLSyntax]): Seq[ParameterBinder] = {
        val namedValues = autoNamedValues(keyword, keywords.column)
        columns.map(namedValues(_))
      }
      items match {
        case Seq() => true
        case _ =>
          val columnsNames: Seq[SQLSyntax] = keywords.column.columns.toSeq
          withSQL {
            insert
              .into(keywords)
              .columns(columnsNames: _*)
              .multipleValues(items.map(values(_, columnsNames)): _*)
          }.execute().apply()
      }
    }

  }
}
