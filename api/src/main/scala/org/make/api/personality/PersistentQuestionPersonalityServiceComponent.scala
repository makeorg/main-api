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

package org.make.api.personality

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.personality.DefaultPersistentQuestionPersonalityServiceComponent.PersistentPersonality
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.personality.{Personality, PersonalityId, PersonalityRole}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentQuestionPersonalityServiceComponent {
  def persistentQuestionPersonalityService: PersistentQuestionPersonalityService
}

trait PersistentQuestionPersonalityService {
  def persist(personality: Personality): Future[Personality]
  def modify(personality: Personality): Future[Personality]
  def getById(personalityId: PersonalityId): Future[Option[Personality]]
  def find(start: Int,
           end: Option[Int],
           sort: Option[String],
           order: Option[String],
           userId: Option[UserId],
           questionId: Option[QuestionId],
           personalityRole: Option[PersonalityRole]): Future[Seq[Personality]]
  def count(userId: Option[UserId],
            questionId: Option[QuestionId],
            personalityRole: Option[PersonalityRole]): Future[Int]
  def delete(personalityId: PersonalityId): Future[Unit]
}

trait DefaultPersistentQuestionPersonalityServiceComponent extends PersistentQuestionPersonalityServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentQuestionPersonalityService: DefaultPersistentQuestionPersonalityService =
    new DefaultPersistentQuestionPersonalityService

  class DefaultPersistentQuestionPersonalityService extends PersistentQuestionPersonalityService with ShortenedNames {

    private val personalityAlias = PersistentPersonality.personalityAlias

    private val column = PersistentPersonality.column

    override def persist(personality: Personality): Future[Personality] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentPersonality)
            .namedValues(
              column.id -> personality.personalityId.value,
              column.userId -> personality.userId.value,
              column.questionId -> personality.questionId.value,
              column.personalityRole -> personality.personalityRole.shortName
            )
        }.execute().apply()
      }).map(_ => personality)
    }

    override def modify(personality: Personality): Future[Personality] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          update(PersistentPersonality)
            .set(
              column.userId -> personality.userId.value,
              column.questionId -> personality.questionId.value,
              column.personalityRole -> personality.personalityRole.shortName
            )
            .where(sqls.eq(column.id, personality.personalityId.value))
        }.execute().apply()
      }).map(_ => personality)
    }

    override def getById(personalityId: PersonalityId): Future[Option[Personality]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB(Symbol("READ")).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentPersonality.as(personalityAlias))
            .where(sqls.eq(personalityAlias.id, personalityId.value))
        }.map(PersistentPersonality.apply()).single.apply()
      }).map(_.map(_.toPersonality))
    }

    override def find(start: Int,
                      end: Option[Int],
                      sort: Option[String],
                      order: Option[String],
                      userId: Option[UserId],
                      questionId: Option[QuestionId],
                      personalityRole: Option[PersonalityRole]): Future[Seq[Personality]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB(Symbol("READ")).retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[WrappedResultSet] = select
            .from(PersistentPersonality.as(personalityAlias))
            .where(
              sqls.toAndConditionOpt(
                userId.map(userId         => sqls.eq(personalityAlias.userId, userId.value)),
                questionId.map(questionId => sqls.eq(personalityAlias.questionId, questionId.value)),
                personalityRole.map(role  => sqls.eq(personalityAlias.personalityRole, role.shortName))
              )
            )

          val queryOrdered = (sort, order) match {
            case (Some(field), Some("DESC")) if PersistentPersonality.columnNames.contains(field) =>
              query.orderBy(personalityAlias.field(field)).desc.offset(start)
            case (Some(field), _) if PersistentPersonality.columnNames.contains(field) =>
              query.orderBy(personalityAlias.field(field)).asc.offset(start)
            case (Some(_), _) =>
              query.orderBy(personalityAlias.id).asc.offset(start)
            case (_, _) => query.orderBy(personalityAlias.id).asc.offset(start)
          }
          end match {
            case Some(limit) => queryOrdered.limit(limit)
            case None        => queryOrdered
          }
        }.map(PersistentPersonality.apply()).list().apply()
      }).map(_.map(_.toPersonality))
    }

    def count(userId: Option[UserId],
              questionId: Option[QuestionId],
              personalityRole: Option[PersonalityRole]): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB(Symbol("READ")).retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentPersonality.as(personalityAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId => sqls.eq(personalityAlias.questionId, questionId.value)),
                userId.map(userId         => sqls.eq(personalityAlias.userId, userId.value)),
                personalityRole.map(role  => sqls.eq(personalityAlias.personalityRole, role.shortName))
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
    }

    override def delete(personalityId: PersonalityId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentPersonality)
            .where(sqls.eq(PersistentPersonality.column.id, personalityId.value))
        }.execute().apply()
      }).map(_ => ())
    }

  }
}

object DefaultPersistentQuestionPersonalityServiceComponent {

  case class PersistentPersonality(id: String, userId: String, questionId: String, personalityRole: String) {
    def toPersonality: Personality = {
      Personality(
        personalityId = PersonalityId(id),
        userId = UserId(userId),
        questionId = QuestionId(questionId),
        personalityRole = PersonalityRole.roleMap(personalityRole)
      )
    }
  }

  object PersistentPersonality extends SQLSyntaxSupport[PersistentPersonality] with ShortenedNames with StrictLogging {
    override val columnNames: Seq[String] =
      Seq("id", "user_id", "question_id", "personality_role")

    override val tableName: String = "personality"

    lazy val personalityAlias: SyntaxProvider[PersistentPersonality] = syntax("personality")

    def apply(
      personalityResultName: ResultName[PersistentPersonality] = personalityAlias.resultName
    )(resultSet: WrappedResultSet): PersistentPersonality = {
      PersistentPersonality.apply(
        id = resultSet.string(personalityResultName.id),
        userId = resultSet.string(personalityResultName.userId),
        questionId = resultSet.string(personalityResultName.questionId),
        personalityRole = resultSet.string(personalityResultName.personalityRole)
      )
    }
  }

}