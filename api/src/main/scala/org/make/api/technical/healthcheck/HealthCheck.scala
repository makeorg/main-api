package org.make.api.technical.healthcheck

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import io.circe._
import org.make.api.technical.healthcheck.HealthCheckCommands._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait HealthCheck extends Actor with ActorLogging {

  val techno: String

  def healthCheck(): Future[String]

  override def receive: Receive = {
    case CheckStatus =>
      val originalSender = sender()

      healthCheck().onComplete {
        case Success(status) =>
          originalSender ! HealthCheckSuccess(techno, status)
          self ! PoisonPill
        case Failure(e) =>
          originalSender ! HealthCheckError(techno, e.getMessage)
          self ! PoisonPill
      }
  }
}

object HealthCheckCommands {
  case object CheckStatus
  case object CheckExternalServices
}

sealed trait HealthCheckResponse {
  val service: String
  val message: String
}

object HealthCheckResponse {
  implicit val encoder: Encoder[HealthCheckResponse] = new Encoder[HealthCheckResponse] {
    override def apply(response: HealthCheckResponse): Json =
      Json.obj((response.service, Json.fromString(response.message)))
  }
}

final case class HealthCheckSuccess(override val service: String, override val message: String)
    extends HealthCheckResponse

final case class HealthCheckError(override val service: String, override val message: String)
    extends HealthCheckResponse

trait HealthCheckActorDefinition {
  val name: String
  def props(healthCheckExecutionContext: ExecutionContext): Props
}
