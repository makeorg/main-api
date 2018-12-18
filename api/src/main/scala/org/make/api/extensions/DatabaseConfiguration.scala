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

package org.make.api.extensions

import java.sql.Connection
import java.util.concurrent.{Executors, ThreadPoolExecutor}

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.github.t3hnar.bcrypt._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import kamon.executors
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.make.api.technical.MonitorableExecutionContext
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.{Failure, Success, Try}

class DatabaseConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport
    with StrictLogging {

  private val user: String = configuration.getString("database.user")
  private val password: String = configuration.getString("database.password")

  private val jdbcUrl: String = configuration.getString("database.jdbc-url")

  private val readDatasource = new BasicDataSource()
  readDatasource.setDriverClassName("org.postgresql.Driver")
  readDatasource.setUrl(jdbcUrl)
  readDatasource.setUsername(user)
  readDatasource.setPassword(password)
  readDatasource.setInitialSize(configuration.getInt("database.pools.read.initial-size"))
  readDatasource.setMaxTotal(configuration.getInt("database.pools.read.max-total"))
  readDatasource.setMaxIdle(configuration.getInt("database.pools.read.max-idle"))

  private val readExecutor: ThreadPoolExecutor = Executors
    .newFixedThreadPool(configuration.getInt("database.pools.read.max-total"))
    .asInstanceOf[ThreadPoolExecutor]
  val readThreadPool: MonitorableExecutionContext =
    new MonitorableExecutionContext(readExecutor)

  executors.Executors.register("db-read-executor", readExecutor)

  private val writeDatasource = new BasicDataSource()
  writeDatasource.setDriverClassName("org.postgresql.Driver")
  writeDatasource.setUrl(jdbcUrl)
  writeDatasource.setUsername(user)
  writeDatasource.setPassword(password)
  writeDatasource.setInitialSize(configuration.getInt("database.pools.write.initial-size"))
  writeDatasource.setMaxTotal(configuration.getInt("database.pools.write.max-total"))
  writeDatasource.setMaxIdle(configuration.getInt("database.pools.write.max-idle"))

  private val writeExecutor: ThreadPoolExecutor = Executors
    .newFixedThreadPool(configuration.getInt("database.pools.write.max-total"))
    .asInstanceOf[ThreadPoolExecutor]
  val writeThreadPool: MonitorableExecutionContext =
    new MonitorableExecutionContext(writeExecutor)

  executors.Executors.register("db-write-executor", writeExecutor)

  ConnectionPool.add('READ, new DataSourceConnectionPool(dataSource = readDatasource))
  ConnectionPool.add('WRITE, new DataSourceConnectionPool(dataSource = writeDatasource))

  GlobalSettings.loggingSQLErrors = true
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = true,
    warningEnabled = true,
    warningThresholdMillis = 3000,
    warningLogLevel = 'warn,
    printUnprocessedStackTrace = false,
    logLevel = 'debug
  )

  private val databaseName = writeDatasource.getConnection.getCatalog
  private val defaultClientId: String = configuration.getString("authentication.default-client-id")
  private val defaultClientSecret: String = configuration.getString("authentication.default-client-secret")
  private val adminFirstName: String = configuration.getString("default-admin.first-name")
  private val adminEmail: String = configuration.getString("default-admin.email")
  private val adminPassword: String = configuration.getString("default-admin.password")
  logger.debug(s"Creating database with name: $databaseName")

  Try {
    val connection: Connection = writeDatasource.getConnection
    connection.prepareStatement(s"CREATE DATABASE IF NOT EXISTS $databaseName").execute()
    connection.close()
  } match {
    case Success(_) =>
    case Failure(e) => logger.debug(s"Error when creating database: ${e.getMessage}")
  }

  val setBaselineVersion: Boolean = configuration.getBoolean("database.migration.init-schema")

  val baselineVersion: MigrationVersion =
    MigrationVersion.fromVersion(configuration.getString("database.migration.baseline-version"))

  var flywayBuilder: FluentConfiguration = Flyway
    .configure()
    .dataSource(writeDatasource)
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

  if (!setBaselineVersion && flywayBuilder.getBaselineVersion.compareTo(baselineVersion) < 0) {
    flywayBuilder = flywayBuilder.baselineVersion(baselineVersion)
  }

  val flyway: Flyway = flywayBuilder.load()
  flyway.migrate()
  Try(flyway.validate()) match {
    case Success(_) => logger.info("SQL migrations: success")
    case Failure(e) => logger.warn("Cannot migrate database:", e)
  }
}

object DatabaseConfiguration extends ExtensionId[DatabaseConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DatabaseConfiguration =
    new DatabaseConfiguration(system.settings.config.getConfig("make-api"))

  override def lookup(): ExtensionId[DatabaseConfiguration] =
    DatabaseConfiguration

  override def get(system: ActorSystem): DatabaseConfiguration =
    super.get(system)
}
