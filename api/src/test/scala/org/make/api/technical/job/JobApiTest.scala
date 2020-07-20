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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import enumeratum.Circe
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.make.api.MakeApiTestBase
import org.make.api.technical.generator.EntitiesGen.genJob
import org.make.core.job.Job
import org.make.core.job.Job.JobId
import org.scalacheck.Arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.Future

class JobApiTest
    extends MakeApiTestBase
    with DefaultJobApiComponent
    with JobCoordinatorServiceComponent
    with ScalaCheckDrivenPropertyChecks {

  override val jobCoordinatorService: JobCoordinatorService = mock[JobCoordinatorService]

  implicit val arbJob: Arbitrary[Job] = Arbitrary(genJob)
  implicit val jobIdDecoder: Decoder[JobId] = Decoder[String].map(JobId.apply)
  implicit val statusDecoder: Decoder[JobResponse.Status] = Circe.decodeCaseInsensitive(JobResponse.Status)
  implicit val jobResponseDecoder: Decoder[JobResponse] = deriveDecoder

  val routes: Route = sealRoute(jobApi.routes)

  Feature("get job status") {
    Scenario("it works") {
      forAll { job: Job =>
        when(jobCoordinatorService.get(eqTo(job.id))).thenReturn(Future.successful(Some(job)))

        Get(s"/admin/jobs/${job.id.value}") ~> routes ~> check {
          status should be(StatusCodes.Unauthorized)
        }

        Get(s"/admin/jobs/${job.id.value}")
          .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }

        Get(s"/admin/jobs/${job.id.value}")
          .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
          status should be(StatusCodes.Forbidden)
        }

        Get(s"/admin/jobs/${job.id.value}")
          .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
          entityAs[JobResponse] should be(JobResponse(job))
        }
      }
    }
  }

}
