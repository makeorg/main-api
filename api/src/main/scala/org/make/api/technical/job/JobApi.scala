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

package org.make.api.technical.job

import java.time.ZonedDateTime

import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import enumeratum.{Circe, Enum, EnumEntry}
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.job.Job
import org.make.core.job.Job.JobId
import org.make.core.job.Job.JobStatus._
import org.make.core.{CirceFormatters, HttpCodes}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "AdminJob")
@Path(value = "/admin/jobs")
trait JobApi extends Directives {

  @ApiOperation(
    value = "get-job-status",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "jobId", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[JobResponse])))
  @Path(value = "/{jobId}")
  def status: Route

  def routes: Route = status

}

trait JobApiComponent {
  def jobApi: JobApi
}

trait DefaultJobApiComponent extends JobApiComponent with MakeAuthenticationDirectives {
  self: IdGeneratorComponent
    with JobCoordinatorServiceComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val jobApi: JobApi = new DefaultJobApi

  class DefaultJobApi extends JobApi {

    val jobId: PathMatcher1[JobId] = Segment.map(id => JobId(id))

    override def status: Route = get {
      path("admin" / "jobs" / jobId) { id =>
        makeOperation("GetJobStatus") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(jobCoordinatorService.get(id)) { job =>
                complete(JobResponse(job))
              }
            }
          }
        }
      }
    }

  }

}

final case class JobResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  id: JobId,
  @(ApiModelProperty @field)(dataType = "dateTime")
  createdAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "dateTime")
  updatedAt: Option[ZonedDateTime],
  @(ApiModelProperty @field)(dataType = "string", example = "finished")
  status: JobResponse.Status,
  details: Option[String]
)

object JobResponse extends CirceFormatters {

  def apply(job: Job): JobResponse = {
    val (status, details) = job.status match {
      case Running(progress) =>
        (if (job.isStuck(Job.defaultHeartRate)) Status.Stuck else Status.Running, Some(s"progress = $progress%"))
      case Finished(error @ Some(_)) =>
        (Status.Failure, error)
      case Finished(_) =>
        (Status.Success, None)
    }
    JobResponse(job.id, job.createdAt, job.updatedAt, status, details)
  }

  sealed abstract class Status extends EnumEntry with Product with Serializable

  object Status extends Enum[Status] {

    case object Running extends Status
    case object Stuck extends Status
    case object Success extends Status
    case object Failure extends Status

    override val values: IndexedSeq[Status] = findValues

    implicit val encoder: Encoder[Status] = Circe.encoderLowercase(this)

  }

  implicit val encoder: Encoder[JobResponse] = deriveEncoder

}
