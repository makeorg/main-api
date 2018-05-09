package org.make.api.technical.crm

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.crm.PublishedCrmContactEvent.CrmContactListSync
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
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
    with MakeSettingsComponent =>

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
            eventBusService.publish(CrmContactListSync(id = auth.user.userId))
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  val crmRoutes: Route = webHook ~ syncCrmData
}
