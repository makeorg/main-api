package org.make.api.extensions

import java.util.concurrent.Executors

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.dbcp2.BasicDataSource
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.io.Source
import scala.util.{Failure, Success, Try}

class DatabaseConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport
    with StrictLogging {

  private val user: String = configuration.getString("user")
  private val password: String = configuration.getString("password")

  private val jdbcUrl: String = configuration.getString("jdbc-url")

  private val autoCreateSchemas: Boolean =
    configuration.getBoolean("auto-create-db-schemas")

  private val readDatasource = new BasicDataSource()
  readDatasource.setDriverClassName("org.postgresql.Driver")
  readDatasource.setUrl(jdbcUrl)
  readDatasource.setUsername(user)
  readDatasource.setPassword(password)
  readDatasource.setInitialSize(configuration.getInt("pools.read.initial-size"))
  readDatasource.setMaxTotal(configuration.getInt("pools.read.max-total"))
  readDatasource.setMaxIdle(configuration.getInt("pools.read.max-idle"))

  val readThreadPool: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(configuration.getInt("pools.read.max-total")))

  private val writeDatasource = new BasicDataSource()
  writeDatasource.setDriverClassName("org.postgresql.Driver")
  writeDatasource.setUrl(jdbcUrl)
  writeDatasource.setUsername(user)
  writeDatasource.setPassword(password)
  writeDatasource.setInitialSize(configuration.getInt("pools.write.initial-size"))
  writeDatasource.setMaxTotal(configuration.getInt("pools.write.max-total"))
  writeDatasource.setMaxIdle(configuration.getInt("pools.write.max-idle"))

  val writeThreadPool: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(configuration.getInt("pools.write.max-total")))

  ConnectionPool.add('READ, new DataSourceConnectionPool(dataSource = readDatasource))

  ConnectionPool.add('WRITE, new DataSourceConnectionPool(dataSource = writeDatasource))

  GlobalSettings.loggingSQLErrors = true
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = true,
    warningEnabled = false,
    printUnprocessedStackTrace = false,
    logLevel = 'info
  )

  if (autoCreateSchemas) {
    val dbname = writeDatasource.getConnection.getCatalog
    logger.debug(s"Creating database with name: $dbname")
    val queries = Source
      .fromResource("create-schema.sql")
      .mkString
      .replace("$dbname", dbname)

    def createSchema: Boolean =
      writeDatasource.getConnection.createStatement.execute(queries)

    Try(createSchema) match {
      case Success(_) => logger.debug("Database schema created.")
      case Failure(e) =>
        logger.error(s"Cannot create schema: ${e.getStackTrace.mkString("\n")}")
    }
  }
}

object DatabaseConfiguration extends ExtensionId[DatabaseConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DatabaseConfiguration =
    new DatabaseConfiguration(system.settings.config.getConfig("make-api.database"))

  override def lookup(): ExtensionId[DatabaseConfiguration] =
    DatabaseConfiguration

  override def get(system: ActorSystem): DatabaseConfiguration =
    super.get(system)
}
