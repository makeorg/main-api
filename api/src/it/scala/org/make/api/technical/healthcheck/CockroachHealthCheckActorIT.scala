package org.make.api.technical.healthcheck

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.github.t3hnar.bcrypt._
import com.typesafe.config.ConfigFactory
import org.apache.commons.dbcp2.BasicDataSource
import org.flywaydb.core.Flyway
import org.make.api.ItMakeTest
import org.make.api.docker.DockerCockroachService
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class CockroachHealthCheckActorIT
    extends TestKit(CockroachHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with ItMakeTest
    with DockerCockroachService {

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
    val adminEmail: String = "admin@make.org"
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
    system.terminate()
  }

  implicit val timeout: Timeout = TimeSettings.defaultTimeout
  feature("Check Cockroach status") {
    scenario("read record where email is admin@make.org") {
      Given("a cockroach health check actor")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckCockroach: ActorRef = actorSystem.actorOf(
        CockroachHealthCheckActor.props(healthCheckExecutionContext),
        CockroachHealthCheckActor.name
      )

      When("I send a message to check the status of cockroach")
      healthCheckCockroach ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("cockroach", "OK"))
    }
  }
}

object CockroachHealthCheckActorIT {
  val cockroachExposedPort = 36257
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    s"""
       |make-api.database.jdbc-url = "jdbc:postgresql://localhost:$cockroachExposedPort/makeapi"
    """.stripMargin

  val actorSystem = ActorSystem("CockroachHealthCheckActorIT", ConfigFactory.parseString(configuration))

}
