package org.make.api.extensions

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.dbcp2.BasicDataSource
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.io.Source
import scala.util.{Failure, Success, Try}


class DatabaseConfiguration(override protected val configuration: Config) extends Extension
  with ConfigurationSupport
  with StrictLogging {

  private val user: String = configuration.getString("user")
  private val password: String = configuration.getString("password")

  private val jdbcUrl: String = configuration.getString("jdbc-url")

  private val autoCreateSchemas: Boolean = configuration.getBoolean("auto-create-db-schemas")

  val readDatasource = new BasicDataSource()
  readDatasource.setDriverClassName("org.postgresql.Driver")
  readDatasource.setUrl(jdbcUrl)
  readDatasource.setUsername(user)
  readDatasource.setPassword(password)
  readDatasource.setInitialSize(10)
  readDatasource.setMaxTotal(50)
  readDatasource.setMaxIdle(10)

  val writeDatasource = new BasicDataSource()
  writeDatasource.setDriverClassName("org.postgresql.Driver")
  writeDatasource.setUrl(jdbcUrl)
  writeDatasource.setUsername(user)
  writeDatasource.setPassword(password)
  writeDatasource.setInitialSize(10)
  writeDatasource.setMaxTotal(50)
  writeDatasource.setMaxIdle(10)



  ConnectionPool.add('READ,
    new DataSourceConnectionPool(
      dataSource = readDatasource
    )
  )

  ConnectionPool.add('WRITE,
    new DataSourceConnectionPool(
      dataSource = writeDatasource
    )
  )


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
    val queries = Source.fromResource("create-schema.sql").mkString.replace(s"$$dbname", dbname)
    def createSchema = writeDatasource.getConnection.createStatement.execute(queries)
    Try(createSchema) match {
      case Success(_) => logger.debug("Database schema created.")
      case Failure(e) => logger.error(s"Cannot create schema: ${e.getStackTrace.mkString("\n")}")
    }
  }
}

object DatabaseConfiguration extends ExtensionId[DatabaseConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DatabaseConfiguration =
    new DatabaseConfiguration(system.settings.config.getConfig("make-api.database"))

  override def lookup(): ExtensionId[DatabaseConfiguration] = DatabaseConfiguration
  override def get(system: ActorSystem): DatabaseConfiguration = super.get(system)
}
