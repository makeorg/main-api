package org.make.api.technical.healthcheck

import akka.actor.Props
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

class CockroachHealthCheckActor(healthCheckExecutionContext: ExecutionContext) extends HealthCheck with ShortenedNames {

  override val techno: String = "cockroach"

  override def healthCheck(): Future[String] = {
    implicit val cxt: EC = healthCheckExecutionContext

    val futureResults: Future[List[Map[String, Any]]] = Future(NamedDB('READ).retryableTx { implicit session =>
      sql"select first_name from make_user where email='admin@make.org'".map(_.toMap).list.apply()
    })

    futureResults
      .map(_.length)
      .map {
        case 1 => "OK"
        case other =>
          log.warning(s"""Unexpected result in cockroach health check: expected "1" result but got "$other"""")
          "NOK"
      }
  }
}

object CockroachHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "cockroach-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new CockroachHealthCheckActor(healthCheckExecutionContext))
}
