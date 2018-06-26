/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

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
