package org.make.api.technical.healthcheck

import akka.actor.{ActorSystem, Props}
import org.make.api.ActorSystemComponent
import org.make.api.technical.ShortenedNames
import org.make.api.technical.storage.DefaultSwiftClientComponent

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class SwiftHealthCheckActor(healthCheckExecutionContext: ExecutionContext)
    extends HealthCheck
    with ShortenedNames
    with DefaultSwiftClientComponent
    with ActorSystemComponent {

  override val techno: String = "swift"

  override def actorSystem: ActorSystem = context.system

  lazy val client = swiftClient

  override def preStart(): Unit = {
    Await.result(client.init(), atMost = 30.seconds)
  }

  override def healthCheck(): Future[String] = {
    implicit val cxt: EC = healthCheckExecutionContext
    client
      .listBuckets()
      .map {
        case Seq() =>
          log.warning("Unexpected result in swift health check: containers list is empty")
          "NOK"
        case _ => "OK"
      }
      .recoverWith {
        case failed =>
          log.error("Error during swift helthcheck: ", failed)
          Future.failed(failed)
      }
  }
}

object SwiftHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "swift-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new SwiftHealthCheckActor(healthCheckExecutionContext))
}
