package org.make.api

import com.github.t3hnar.bcrypt._
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

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    // toDo: remove when resolve DockerCockroachService
    startAllOrFail()

    GlobalSettings.loggingSQLErrors = true
    GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
      enabled = true,
      warningEnabled = false,
      printUnprocessedStackTrace = true,
      logLevel = 'info
    )

    val writeDatasource = new BasicDataSource()
    writeDatasource.setDriverClassName("org.postgresql.Driver")
    writeDatasource.setUrl(s"jdbc:postgresql://localhost:$defaultCockroachPortExposed/makeapitest")
    writeDatasource.setUsername("root")

    ConnectionPool.add('WRITE, new DataSourceConnectionPool(dataSource = writeDatasource))
    ConnectionPool.add('READ, new DataSourceConnectionPool(dataSource = writeDatasource))

    val dbname: String = "makeapitest"
    val defaultClientId: String = "clientId"
    val defaultClientSecret: String = "clientSecret"
    val adminFirstName: String = "admin"
    val adminEmail: String = "admin@example.com"
    val adminPassword: String = "passpass".bcrypt
    logger.debug(s"Creating database with name: $dbname")

    val flyway: Flyway = new Flyway()
    flyway.setDataSource(writeDatasource)
    flyway.setBaselineOnMigrate(true)
    flyway.setPlaceholders(
      Map(
        "dbname" -> dbname,
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

  override protected def afterAll(): Unit = {

    super.afterAll()
    stopAllQuietly()
  }
}
