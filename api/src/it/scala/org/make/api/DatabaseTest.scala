package org.make.api

import org.apache.commons.dbcp2.BasicDataSource
import org.make.api.extensions.MakeDBExecutionContextComponent
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool, GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Failure, Success, Try}

trait DatabaseTest extends MakeTest with DockerCockroachService with MakeDBExecutionContextComponent {

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

    val queries = Source
      .fromString("DROP DATABASE IF EXISTS makeapitest;")
      .mkString
      .concat(
        Source
          .fromResource("create-schema.sql")
          .mkString
          .replace("#dbname#", "makeapitest")
          .replace("#clientid#", "clientId")
          .replace("#clientsecret#", "clientSecret")
      )
      .split("%")

    val conn = writeDatasource.getConnection
    def createSchema = queries.map(query => Try(conn.createStatement.execute(query)))

    Try(createSchema) match {
      case Success(_) => logger.debug("Database schema created.")
      case Failure(e) =>
        logger.error(s"Cannot create schema: ${e.getStackTrace.mkString("\n")}")
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }
}
