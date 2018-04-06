package org.make.api.technical.healthcheck

import akka.actor.ActorRef
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckExternalServices
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.technical.TimeSettings

import scala.concurrent.Future

trait HealthCheckComponent {
  def healthCheckSupervisor: ActorRef
}

trait HealthCheckService {
  def runAllHealthChecks(): Future[Seq[HealthCheckResponse]]
}

trait HealthCheckServiceComponent {
  def healthCheckService: HealthCheckService
}

trait DefaultHealthCheckServiceComponent extends HealthCheckServiceComponent {
  self: HealthCheckComponent =>
  override def healthCheckService: HealthCheckService = new HealthCheckService {
    private implicit val timeout: Timeout = TimeSettings.defaultTimeout.duration * 2
    override def runAllHealthChecks(): Future[Seq[HealthCheckResponse]] = {
      (healthCheckSupervisor ? CheckExternalServices).mapTo[Seq[HealthCheckResponse]]
    }
  }
}
