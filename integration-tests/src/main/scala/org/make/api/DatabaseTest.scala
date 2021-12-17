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

package org.make.api

import com.github.t3hnar.bcrypt._
import javax.sql.DataSource
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import org.make.api.docker.DockerCockroachService
import org.make.api.extensions.MakeDBExecutionContextComponent
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.DurationInt

trait DatabaseTest extends MakeUnitTest with DockerCockroachService with MakeDBExecutionContextComponent {

  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global

  protected def databaseName: String = "makeapitest"

  protected def defaultClientId: String = "clientId"
  protected def defaultClientSecret: String = "clientSecret"
  protected def adminFirstName: String = "admin"
  protected def adminEmail: String = "admin@example.com"
  protected def adminPassword: String = "passpass".bcrypt

  override def beforeAll(): Unit = {
    super.beforeAll()

    GlobalSettings.loggingSQLErrors = true
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      warningEnabled = false,
      printUnprocessedStackTrace = true,
      logLevel = Symbol("info")
    )

    val dataSource: DataSource = createDataSource

    logger.debug(s"Creating database with name: $databaseName")

    val connection = dataSource.getConnection
    connection.prepareStatement(s"CREATE DATABASE $databaseName").execute()
    connection.close()

    ConnectionPool.add("WRITE", new DataSourceConnectionPool(dataSource))
    ConnectionPool.add("READ", new DataSourceConnectionPool(dataSource))

    Thread.sleep(1.second.toMillis)

    val flyway: Flyway = Flyway
      .configure()
      .dataSource(dataSource)
      .baselineOnMigrate(true)
      .locations("classpath:db/migration", "classpath:org/make/api/migrations/db")
      .placeholders(
        Map(
          "dbname" -> databaseName,
          "clientId" -> defaultClientId,
          "clientSecret" -> defaultClientSecret,
          "adminEmail" -> adminEmail,
          "adminFirstName" -> adminFirstName,
          "adminEncryptedPassword" -> adminPassword.bcrypt
        ).asJava
      )
      .load()

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    @tailrec
    def migrateFlyway(nRetries: Int = 3): Unit =
      Try(flyway.migrate()) match {
        case Success(_)                  =>
        case Failure(e) if nRetries <= 0 => throw e
        case _ =>
          flyway.repair()
          migrateFlyway(nRetries - 1)
      }

    migrateFlyway()

    Try(flyway.validate()) match {
      case Success(_) => logger.info("Database schema created")
      case Failure(e) => logger.warn("Cannot migrate database:", e)
    }

  }

  private def createDataSource: DataSource = {
    val writeDataSource = new BasicDataSource()
    writeDataSource.setDriverClassName("org.postgresql.Driver")
    writeDataSource.setUrl(s"jdbc:postgresql://localhost:$cockroachExposedPort/$databaseName")
    writeDataSource.setUsername("root")
    writeDataSource
  }
}
