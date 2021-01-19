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

package org.make.api.technical.auth

import java.time.ZonedDateTime

import cats.data.NonEmptyList
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.auth.PersistentClientServiceComponent.PersistentClient
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.DateHelper
import org.make.core.auth.{Client, ClientId}
import org.make.core.user.{Role, UserId}
import scalikejdbc._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentClientServiceComponent {
  def persistentClientService: PersistentClientService
}

object PersistentClientServiceComponent {

  val GRANT_TYPE_SEPARATOR = ","
  val ROLE_SEPARATOR = ","

  final case class PersistentClient(
    uuid: String,
    name: String,
    allowedGrantTypes: String,
    secret: Option[String],
    scope: Option[String],
    redirectUri: Option[String],
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    defaultUserId: Option[String],
    roles: String,
    tokenExpirationSeconds: Int,
    refreshExpirationSeconds: Int,
    reconnectExpirationSeconds: Int
  ) {
    def toClient: Client =
      Client(
        clientId = ClientId(uuid),
        name = name,
        allowedGrantTypes = allowedGrantTypes.split(GRANT_TYPE_SEPARATOR).toIndexedSeq,
        secret = secret,
        scope = scope,
        redirectUri = redirectUri,
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt),
        defaultUserId = defaultUserId.map(UserId(_)),
        roles = roles.split(ROLE_SEPARATOR).toIndexedSeq.map(Role.apply),
        tokenExpirationSeconds = tokenExpirationSeconds,
        refreshExpirationSeconds = refreshExpirationSeconds,
        reconnectExpirationSeconds = reconnectExpirationSeconds
      )
  }

  implicit object PersistentClient
      extends PersistentCompanion[PersistentClient, Client]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq(
        "uuid",
        "name",
        "secret",
        "allowed_grant_types",
        "scope",
        "redirect_uri",
        "created_at",
        "updated_at",
        "default_user_id",
        "roles",
        "token_expiration_seconds",
        "refresh_expiration_seconds",
        "reconnect_expiration_seconds"
      )

    override val tableName: String = "oauth_client"

    override lazy val alias: SyntaxProvider[PersistentClient] = syntax("c")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.uuid)

    def apply(
      clientResultName: ResultName[PersistentClient] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentClient = {
      PersistentClient(
        uuid = resultSet.string(clientResultName.uuid),
        name = resultSet.string(clientResultName.name),
        allowedGrantTypes = resultSet.string(clientResultName.allowedGrantTypes),
        secret = resultSet.stringOpt(clientResultName.secret),
        scope = resultSet.stringOpt(clientResultName.scope),
        redirectUri = resultSet.stringOpt(clientResultName.redirectUri),
        createdAt = resultSet.zonedDateTime(clientResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(clientResultName.updatedAt),
        defaultUserId = resultSet.stringOpt(clientResultName.defaultUserId),
        roles = resultSet.string(clientResultName.roles),
        tokenExpirationSeconds = resultSet.int(clientResultName.tokenExpirationSeconds),
        refreshExpirationSeconds = resultSet.int(clientResultName.refreshExpirationSeconds),
        reconnectExpirationSeconds = resultSet.int(clientResultName.reconnectExpirationSeconds)
      )
    }
  }

}

trait PersistentClientService {
  def get(clientId: ClientId): Future[Option[Client]]
  def findByClientIdAndSecret(clientId: String, secret: Option[String]): Future[Option[Client]]
  def persist(client: Client): Future[Client]
  def update(client: Client): Future[Option[Client]]
  def search(start: Start, end: Option[End], name: Option[String]): Future[Seq[Client]]
  def count(name: Option[String]): Future[Int]
}

trait DefaultPersistentClientServiceComponent extends PersistentClientServiceComponent {
  self: MakeDBExecutionContextComponent =>

  override lazy val persistentClientService: DefaultPersistentClientService = new DefaultPersistentClientService

  class DefaultPersistentClientService extends PersistentClientService with ShortenedNames with Logging {

    private val clientAlias = PersistentClient.alias
    private val column = PersistentClient.column

    override def get(clientId: ClientId): Future[Option[Client]] = {
      implicit val cxt: EC = readExecutionContext
      val futureClient = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentClient.as(clientAlias))
            .where(sqls.eq(clientAlias.uuid, clientId.value))
        }.map(PersistentClient.apply()).single().apply()
      })

      futureClient.map(_.map(_.toClient))
    }

    override def findByClientIdAndSecret(clientId: String, secret: Option[String]): Future[Option[Client]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentClient = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentClient.as(clientAlias))
            .where(
              sqls
                .eq(clientAlias.uuid, clientId)
                //TODO: Test this function with both secret value: Some(secret) || None
                .and(sqls.eq(clientAlias.secret, secret))
            )
        }.map(PersistentClient.apply()).single().apply()
      })

      futurePersistentClient.map(_.map(_.toClient))
    }

    override def persist(client: Client): Future[Client] = {
      implicit val ctx: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentClient)
            .namedValues(
              column.uuid -> client.clientId.value,
              column.name -> client.name,
              column.allowedGrantTypes -> client.allowedGrantTypes.mkString(
                PersistentClientServiceComponent.GRANT_TYPE_SEPARATOR
              ),
              column.secret -> client.secret,
              column.redirectUri -> client.redirectUri,
              column.scope -> client.scope,
              column.createdAt -> nowDate,
              column.updatedAt -> nowDate,
              column.defaultUserId -> client.defaultUserId.map(_.value),
              column.roles -> client.roles.map(_.value).mkString(PersistentClientServiceComponent.ROLE_SEPARATOR),
              column.tokenExpirationSeconds -> client.tokenExpirationSeconds,
              column.refreshExpirationSeconds -> client.refreshExpirationSeconds,
              column.reconnectExpirationSeconds -> client.reconnectExpirationSeconds
            )
        }.execute().apply()
      }).map(_ => client)
    }

    override def update(client: Client): Future[Option[Client]] = {
      implicit val ctx: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          scalikejdbc
            .update(PersistentClient)
            .set(
              column.name -> client.name,
              column.allowedGrantTypes -> client.allowedGrantTypes.mkString(
                PersistentClientServiceComponent.GRANT_TYPE_SEPARATOR
              ),
              column.secret -> client.secret,
              column.redirectUri -> client.redirectUri,
              column.scope -> client.scope,
              column.createdAt -> nowDate,
              column.updatedAt -> nowDate,
              column.defaultUserId -> client.defaultUserId.map(_.value),
              column.roles -> client.roles.map(_.value).mkString(PersistentClientServiceComponent.ROLE_SEPARATOR),
              column.tokenExpirationSeconds -> client.tokenExpirationSeconds,
              column.refreshExpirationSeconds -> client.refreshExpirationSeconds,
              column.reconnectExpirationSeconds -> client.reconnectExpirationSeconds
            )
            .where(sqls.eq(column.uuid, client.clientId.value))
        }.executeUpdate().apply()
      }).map {
        case 1 => Some(client)
        case 0 =>
          logger.error(s"Client '${client.clientId.value}' not found")
          None
      }
    }

    override def search(start: Start, end: Option[End], name: Option[String]): Future[Seq[Client]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentClients: Future[List[PersistentClient]] = Future(NamedDB("READ").retryableTx {
        implicit session =>
          withSQL {

            val query: scalikejdbc.PagingSQLBuilder[PersistentClient] =
              select
                .from(PersistentClient.as(clientAlias))
                .where(
                  sqls.toAndConditionOpt(
                    name
                      .map(n => sqls.like(sqls"lower(${clientAlias.name})", s"%${n.toLowerCase.replace("%", "\\%")}%"))
                  )
                )

            sortOrderQuery(start, end, None, None, query)
          }.map(PersistentClient.apply()).list().apply()
      })

      futurePersistentClients.map(_.map(_.toClient))
    }

    override def count(name: Option[String]): Future[Int] = {
      implicit val context: EC = readExecutionContext

      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {

          select(sqls.count)
            .from(PersistentClient.as(clientAlias))
            .where(
              sqls.toAndConditionOpt(
                name
                  .map(n => sqls.like(sqls"lower(${clientAlias.name})", s"%${n.toLowerCase.replace("%", "\\%")}%"))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }
  }
}
