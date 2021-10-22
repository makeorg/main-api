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

package org.make.api.user

import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions.RichDatabase
import org.make.api.technical.ShortenedNames
import org.make.api.user.PersistentCrmSynchroUserService.{CrmSynchroUser, MakeUser}
import scalikejdbc._
import org.make.api.technical.ScalikeSupport._

import scala.concurrent.Future

trait DefaultPersistentCrmSynchroUserServiceComponent extends PersistentCrmSynchroUserServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentCrmSynchroUserService: PersistentCrmSynchroUserService =
    new DefaultPersistentCrmSynchroUserService

  final class DefaultPersistentCrmSynchroUserService extends PersistentCrmSynchroUserService with ShortenedNames {

    private val synchroUser = SQLSyntaxSupportFactory[MakeUser]()
    private val u = synchroUser.syntax

    override def findUsersForCrmSynchro(
      optIn: Option[Boolean],
      hardBounce: Option[Boolean],
      offset: Int,
      limit: Int
    ): Future[Seq[CrmSynchroUser]] = {
      implicit val cxt: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(synchroUser.as(u))
            .where(
              sqls.toAndConditionOpt(
                hardBounce.map(sqls.eq(u.isHardBounce, _)),
                optIn.map(sqls.eq(u.optInNewsletter, _)),
                Some(sqls.notLike(u.email, "yopmail+%@make.org"))
              )
            )
            .orderBy(u.createdAt.asc, u.email.asc)
            .limit(limit)
            .offset(offset)
        }.map(synchroUser.apply(u.resultName))
          .list()
          .apply()
      })
    }
  }
}
