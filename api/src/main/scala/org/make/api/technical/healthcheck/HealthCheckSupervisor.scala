package org.make.api.technical.healthcheck

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HealthCheckSupervisor extends Actor with ActorLogging {

  val healthCheckActorDefinitions: Seq[HealthCheckActorDefinition] =
    Seq(ZookeeperHealthCheckActor, CockroachHealthCheckActor)

  private implicit val timeout: Timeout = TimeSettings.defaultTimeout

  val healthCheckExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  override def preStart(): Unit = {
    healthCheckActorDefinitions.map { actor =>
      context.watch(context.actorOf(actor.props(healthCheckExecutionContext), actor.name))
    }
  }

  override def receive: Receive = {
    case CheckExternalServices =>
      Future
        .traverse(context.children) { hc =>
          (hc ? CheckStatus).mapTo[HealthCheckResponse]
        }
        .pipeTo(sender())
    case x => log.info(s"received $x")
  }
}

object HealthCheckSupervisor {
  val name: String = "health-checks"

  def props: Props = Props[HealthCheckSupervisor]
}
