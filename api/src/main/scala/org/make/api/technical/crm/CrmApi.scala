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

package org.make.api.technical.crm

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.api.technical.crm.PublishedCrmContactEvent.CrmContactListSync
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.{DateHelper, HttpCodes}
import org.make.core.auth.UserRights
import scalaoauth2.provider.AuthInfo

@Api(value = "CRM")
@Path(value = "/")
trait CrmApi extends MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with EventBusServiceComponent
    with MailJetConfigurationComponent
    with EventBusServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication =>

  private def authenticate(credentials: Credentials): Option[String] = {
    val login = mailJetConfiguration.basicAuthLogin
    val password = mailJetConfiguration.basicAuthPassword
    credentials match {
      case c @ Provided(`login`) if c.verify(password, _.trim) => Some("OK")
      case _                                                   => None
    }
  }
  @ApiOperation(
    value = "consume-mailjet-event",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(new Authorization(value = "basicAuth"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[String])))
  @Path(value = "/technical/mailjet")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.technical.crm.MailJetEvent")
    )
  )
  def webHook: Route = {
    post {
      path("technical" / "mailjet") {
        makeOperation("mailjet-webhook") { _ =>
          authenticateBasic[String]("make-mailjet", authenticate).apply { _ =>
            decodeRequest {

              entity(as[Seq[MailJetEvent]]) { events: Seq[MailJetEvent] =>
                // Send all events to event bus
                events.foreach(eventBusService.publish)
                complete(StatusCodes.OK)
              } ~
                entity(as[MailJetEvent]) { event: MailJetEvent =>
                  eventBusService.publish(event)
                  complete(StatusCodes.OK)
                }

            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "sync-crm-data",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[String])))
  @Path(value = "/technical/crm/synchronize")
  def syncCrmData: Route = post {
    path("technical" / "crm" / "synchronize") {
      makeOAuth2 { auth: AuthInfo[UserRights] =>
        requireAdminRole(auth.user) {
          makeOperation("SyncCrmData") { _ =>
            eventBusService.publish(CrmContactListSync(id = auth.user.userId, eventDate = DateHelper.now()))
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  val crmRoutes: Route = webHook ~ syncCrmData
}
