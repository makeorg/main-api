package org.make.api

import com.github.t3hnar.bcrypt._
import javax.sql.DataSource
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import org.make.api.docker.DockerCockroachService
import org.make.api.extensions.MakeDBExecutionContextComponent
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait DatabaseTest extends ItMakeTest with DockerCockroachService with MakeDBExecutionContextComponent {

  override val readExecutionContext: ExecutionContext = ExecutionContext.Implicits.global
  override val writeExecutionContext: ExecutionContext = ExecutionContext.Implicits.global

  protected def databaseName: String = "makeapitest"

  protected def defaultClientId: String = "clientId"
  protected def defaultClientSecret: String = "clientSecret"
  protected def adminFirstName: String = "admin"
  protected def adminEmail: String = "admin@example.com"
  protected def adminPassword: String = "passpass".bcrypt

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    startAllOrFail()

    GlobalSettings.loggingSQLErrors = true
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      warningEnabled = false,
      printUnprocessedStackTrace = true,
      logLevel = 'info
    )

    val dataSource: DataSource = createDataSource

    logger.debug(s"Creating database with name: $databaseName")

    val connection = dataSource.getConnection
    connection.prepareStatement(s"CREATE DATABASE $databaseName").execute()
    connection.close()

    ConnectionPool.add('WRITE, new DataSourceConnectionPool(dataSource))
    ConnectionPool.add('READ, new DataSourceConnectionPool(dataSource))

    val flyway: Flyway = new Flyway()
    flyway.setDataSource(dataSource)
    flyway.setBaselineOnMigrate(true)
    flyway.setPlaceholders(
      Map(
        "clientId" -> defaultClientId,
        "clientSecret" -> defaultClientSecret,
        "adminEmail" -> adminEmail,
        "adminFirstName" -> adminFirstName,
        "adminEncryptedPassword" -> adminPassword.bcrypt
      ).asJava
    )
    flyway.migrate()
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

  override protected def afterAll(): Unit = {

    super.afterAll()
    stopAllQuietly()
  }
}
