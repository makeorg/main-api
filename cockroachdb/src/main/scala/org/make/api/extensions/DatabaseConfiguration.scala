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
import com.github.t3hnar.bcrypt._
import com.typesafe.config.Config
import grizzled.slf4j.Logging
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.make.api.extensions.MakeSettings.DefaultAdmin
import org.make.api.technical.MonitorableExecutionContext
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import org.make.api.technical.ExecutorServiceHelper._

class DatabaseConfiguration(override protected val configuration: Config, defaultAdmin: DefaultAdmin)
    extends ConfigurationSupport
    with Logging {

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

  val (readExecutor: MonitorableExecutionContext, readThreadPool: ExecutionContext) = {
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    val executor = Executors
      .newFixedThreadPool(configuration.getInt("database.pools.read.max-total"))
      .asInstanceOf[ThreadPoolExecutor]
    (new MonitorableExecutionContext(executor), executor.instrument("db-read-executor").toExecutionContext)
  }

  private val writeDatasource = new BasicDataSource()
  writeDatasource.setDriverClassName("org.postgresql.Driver")
  writeDatasource.setUrl(jdbcUrl)
  writeDatasource.setUsername(user)
  writeDatasource.setPassword(password)
  writeDatasource.setInitialSize(configuration.getInt("database.pools.write.initial-size"))
  writeDatasource.setMaxTotal(configuration.getInt("database.pools.write.max-total"))
  writeDatasource.setMaxIdle(configuration.getInt("database.pools.write.max-idle"))

  val (writeExecutor: MonitorableExecutionContext, writeThreadPool: ExecutionContext) = {
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    val executor = Executors
      .newFixedThreadPool(configuration.getInt("database.pools.write.max-total"))
      .asInstanceOf[ThreadPoolExecutor]
    (new MonitorableExecutionContext(executor), executor.instrument("db-write-executor").toExecutionContext)
  }

  ConnectionPool.add("READ", new DataSourceConnectionPool(dataSource = readDatasource))
  ConnectionPool.add("WRITE", new DataSourceConnectionPool(dataSource = writeDatasource))

  GlobalSettings.loggingSQLErrors = true
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = true,
    warningEnabled = true,
    warningThresholdMillis = 500,
    warningLogLevel = Symbol("warn"),
    printUnprocessedStackTrace = false,
    logLevel = Symbol("debug")
  )

  private val databaseName = writeDatasource.getConnection.getCatalog
  private val defaultClientId: String = configuration.getString("authentication.default-client-id")
  private val defaultClientSecret: String = configuration.getString("authentication.default-client-secret")

  def migrateDatabase(): Unit = {
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

    val repair: Boolean = configuration.getBoolean("database.migration.repair")

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
          "adminEmail" -> defaultAdmin.email,
          "adminFirstName" -> defaultAdmin.firstName,
          "adminEncryptedPassword" -> defaultAdmin.password.bcrypt
        ).asJava
      )

    if (!setBaselineVersion && flywayBuilder.getBaselineVersion.compareTo(baselineVersion) < 0) {
      flywayBuilder = flywayBuilder.baselineVersion(baselineVersion)
    }

    val flyway: Flyway = flywayBuilder.load()
    if (repair) {
      Try(flyway.repair()) match {
        case Success(_) => logger.info("Repairing SQL migrations history: success")
        case Failure(e) => logger.warn("Cannot repair SQL migrations history:", e)
      }
    }
    flyway.migrate()
    Try(flyway.validate()) match {
      case Success(_) => logger.info("SQL migrations: success")
      case Failure(e) => logger.warn("Cannot migrate database:", e)
    }
  }
}
