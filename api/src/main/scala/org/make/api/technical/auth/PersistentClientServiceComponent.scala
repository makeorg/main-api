package org.make.api.technical.auth

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.api.technical.auth.PersistentClientServiceComponent.PersistentClient
import org.make.core.DateHelper
import org.make.core.auth.{Client, ClientId}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentClientServiceComponent {
  def persistentClientService: PersistentClientService
}

object PersistentClientServiceComponent {

  val GRANT_TYPE_SEPARATOR = ","

  case class PersistentClient(uuid: String,
                              allowedGrantTypes: String,
                              secret: Option[String],
                              scope: Option[String],
                              redirectUri: Option[String],
                              createdAt: ZonedDateTime,
                              updatedAt: ZonedDateTime) {
    def toClient: Client =
      Client(
        clientId = ClientId(uuid),
        allowedGrantTypes = allowedGrantTypes.split(GRANT_TYPE_SEPARATOR),
        secret = secret,
        scope = scope,
        redirectUri = redirectUri,
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt)
      )
  }

  object PersistentClient extends SQLSyntaxSupport[PersistentClient] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] =
      Seq("uuid", "secret", "allowed_grant_types", "scope", "redirect_uri", "created_at", "updated_at")

    override val tableName: String = "oauth_client"

    lazy val clientAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentClient], PersistentClient] = syntax("c")

    def apply(
      clientResultName: ResultName[PersistentClient] = clientAlias.resultName
    )(resultSet: WrappedResultSet): PersistentClient = {
      PersistentClient(
        uuid = resultSet.string(clientResultName.uuid),
        allowedGrantTypes = resultSet.string(clientResultName.allowedGrantTypes),
        secret = resultSet.stringOpt(clientResultName.secret),
        scope = resultSet.stringOpt(clientResultName.secret),
        redirectUri = resultSet.stringOpt(clientResultName.redirectUri),
        createdAt = resultSet.zonedDateTime(clientResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(clientResultName.updatedAt)
      )
    }
  }

}

trait PersistentClientService {
  def get(clientId: ClientId): Future[Option[Client]]
  def findByClientIdAndSecret(clientId: String, secret: Option[String]): Future[Option[Client]]
  def persist(client: Client): Future[Client]
}

trait DefaultPersistentClientServiceComponent extends PersistentClientServiceComponent {
  self: MakeDBExecutionContextComponent =>

  override lazy val persistentClientService = new PersistentClientService with ShortenedNames with StrictLogging {

    private val clientAlias = PersistentClient.clientAlias
    private val column = PersistentClient.column

    override def get(clientId: ClientId): Future[Option[Client]] = {
      implicit val cxt: EC = readExecutionContext
      val futureClient = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentClient.as(clientAlias))
            .where(sqls.eq(clientAlias.uuid, clientId.value))
        }.map(PersistentClient.apply()).single.apply
      })

      futureClient.map(_.map(_.toClient))
    }

    override def findByClientIdAndSecret(clientId: String, secret: Option[String]): Future[Option[Client]] = {
      implicit val cxt: EC = readExecutionContext
      val futurePersistentClient = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentClient.as(clientAlias))
            .where(
              sqls
                .eq(clientAlias.uuid, clientId)
                //TODO: Test this function with both secret value: Some(secret) || None
                .and(sqls.eq(clientAlias.secret, secret))
            )
        }.map(PersistentClient.apply()).single.apply
      })

      futurePersistentClient.map(_.map(_.toClient))
    }

    override def persist(client: Client): Future[Client] = {
      implicit val ctx: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentClient)
            .namedValues(
              column.uuid -> client.clientId.value,
              column.allowedGrantTypes -> client.allowedGrantTypes.mkString(
                PersistentClientServiceComponent.GRANT_TYPE_SEPARATOR
              ),
              column.secret -> client.secret,
              column.scope -> client.scope,
              column.createdAt -> DateHelper.now(),
              column.updatedAt -> DateHelper.now()
            )
        }.execute().apply()
      }).map(_ => client)
    }
  }
}
