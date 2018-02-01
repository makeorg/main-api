package org.make.api.extensions

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.github.t3hnar.bcrypt._
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import kamon.executors
import org.apache.commons.dbcp2.BasicDataSource
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.ExecutionContext
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

class DatabaseConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport
    with StrictLogging {

  private val user: String = configuration.getString("database.user")
  private val password: String = configuration.getString("database.password")

  private val jdbcUrl: String = configuration.getString("database.jdbc-url")

  private val autoCreateSchemas: Boolean =
    configuration.getBoolean("database.auto-create-db-schemas")

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
    warningEnabled = false,
    printUnprocessedStackTrace = false,
    logLevel = 'debug
  )

  if (autoCreateSchemas) {
    val dbname = writeDatasource.getConnection.getCatalog
    val defaultClientId: String = configuration.getString("authentication.default-client-id")
    val defaultClientSecret: String = configuration.getString("authentication.default-client-secret")
    val adminFirstName: String = configuration.getString("default-admin.first-name")
    val adminEmail: String = configuration.getString("default-admin.email")
    val adminPassword: String = configuration.getString("default-admin.password")
    logger.debug(s"Creating database with name: $dbname")
    val queries = Source
      .fromResource("create-schema.sql")(Codec.UTF8)
      .mkString
      .replace("#dbname#", dbname)
      .replace("#clientid#", defaultClientId)
      .replace("#clientsecret#", defaultClientSecret)
      .replace("#adminemail#", adminEmail)
      .replace("#adminfirstname#", adminFirstName)
      .replace("#adminencryptedpassword#", adminPassword.bcrypt)
      .split("%")

    val conn = writeDatasource.getConnection
    val results = queries.map(query => Try(conn.createStatement.execute(query)))

    results.foreach {
      case Success(_) =>
      case Failure(e) => logger.warn("Error in creation script:", e)
    }
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

class MonitorableExecutionContext(executorService: ThreadPoolExecutor) extends ExecutionContext {
  private val executionContext = ExecutionContext.fromExecutorService(executorService)

  override def execute(runnable: Runnable): Unit = executionContext.execute(runnable)
  override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)

  def activeTasks: Int = executorService.getActiveCount
  def currentTasks: Int = executorService.getPoolSize
  def maxTasks: Int = executorService.getMaximumPoolSize
  def waitingTasks: Int = executorService.getQueue.size()
}
