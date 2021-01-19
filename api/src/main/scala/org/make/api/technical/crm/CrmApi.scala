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
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.{Directives, Route}
import grizzled.slf4j.Logging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.job.Job.JobId.SyncCrmData
import org.make.core.{DateHelper, HttpCodes, Validation}
import scalaoauth2.provider.AuthInfo
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Failure, Success}

@Api(value = "CRM")
@Path(value = "/")
trait CrmApi extends Directives {

  @ApiOperation(
    value = "consume-mailjet-event",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(new Authorization(value = "basicAuth"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/technical/mailjet")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.technical.crm.MailJetEvent")
    )
  )
  def webHook: Route

  @ApiOperation(
    value = "sync-crm-data",
    httpMethod = "POST",
    code = HttpCodes.Accepted,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.Accepted, message = "Accepted"),
      new ApiResponse(code = HttpCodes.Conflict, message = "Conflict")
    )
  )
  @Path(value = "/technical/crm/synchronize")
  def syncCrmData: Route

  @ApiOperation(
    value = "anonymize-users",
    httpMethod = "POST",
    code = HttpCodes.Accepted,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.Accepted, message = "Accepted")))
  @Path(value = "/technical/crm/anonymize")
  def anonymizeUsers: Route

  @ApiOperation(
    value = "send-list",
    httpMethod = "POST",
    code = HttpCodes.Accepted,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.Accepted, message = "Accepted")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "crmList",
        paramType = "path",
        dataType = "string",
        example = "optIn",
        allowableValues = "optIn,optOut,hardBounce"
      )
    )
  )
  @Path(value = "/technical/crm/{crmList}/synchronize")
  def sendListToCrm: Route

  def routes: Route = webHook ~ syncCrmData ~ anonymizeUsers ~ sendListToCrm
}

trait CrmApiComponent {
  def crmApi: CrmApi
}

trait DefaultCrmApiComponent extends CrmApiComponent with MakeAuthenticationDirectives with Logging {
  this: MakeDataHandlerComponent
    with EventBusServiceComponent
    with MailJetConfigurationComponent
    with CrmServiceComponent
    with EventBusServiceComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with MakeAuthentication =>

  override lazy val crmApi: CrmApi = new DefaultCrmApi

  class DefaultCrmApi extends CrmApi {

    private def authenticate(credentials: Credentials): Option[String] = {
      val login = mailJetConfiguration.basicAuthLogin
      val password = mailJetConfiguration.basicAuthPassword
      credentials match {
        case c @ Provided(`login`) if c.verify(password, _.trim) => Some("OK")
        case _                                                   => None
      }
    }

    override def webHook: Route = {
      post {
        path("technical" / "mailjet") {
          makeOperation("mailjet-webhook") { _ =>
            authenticateBasic[String]("make-mailjet", authenticate).apply { _ =>
              decodeRequest {

                entity(as[Seq[MailJetEvent]]) { events: Seq[MailJetEvent] =>
                  // Send all events to event bus
                  events.foreach { event =>
                    Validation.validateOptional(
                      Some(
                        Validation.validateUserInput(
                          fieldValue = event.email,
                          fieldName = "email",
                          message = Some("Invalid email")
                        )
                      ),
                      event.customCampaign.map(
                        customCampaign =>
                          Validation.validateUserInput(
                            fieldValue = customCampaign,
                            fieldName = "customCampaign",
                            message = Some("Invalid customCampaign")
                          )
                      ),
                      event.customId.map(
                        customId =>
                          Validation.validateUserInput(
                            fieldValue = customId,
                            fieldName = "customId",
                            message = Some("Invalid customId")
                          )
                      ),
                      event.payload.map(
                        payload =>
                          Validation.validateUserInput(
                            fieldValue = payload,
                            fieldName = "payload",
                            message = Some("Invalid payload")
                          )
                      )
                    )
                    eventBusService.publish(event)
                  }
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

    override def syncCrmData: Route = post {
      path("technical" / "crm" / "synchronize") {
        makeOperation("CrmSynchro") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              makeOperation(SyncCrmData.value) { _ =>
                // The future will take a lot of time to complete,
                // so it's better to leave it in the background
                provideAsync(crmService.synchronizeContactsWithCrm()) { acceptance =>
                  if (acceptance.isAccepted) {
                    complete(StatusCodes.Accepted)
                  } else {
                    complete(StatusCodes.Conflict)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def anonymizeUsers: Route = post {
      path("technical" / "crm" / "anonymize") {
        makeOperation("CrmAnonymize") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              makeOperation("AnonymizeUsers") { _ =>
                val startTime = System.currentTimeMillis()
                crmService.anonymize().onComplete {
                  case Success(_) =>
                    logger
                      .info(s"anonymizing contacts succeeded in ${System.currentTimeMillis() - startTime}ms")
                  case Failure(e) =>
                    logger
                      .error(s"anonymizing contacts failed in ${System.currentTimeMillis() - startTime}ms", e)
                }
                complete(StatusCodes.Accepted)
              }
            }
          }
        }
      }
    }

    override def sendListToCrm: Route = post {
      path("technical" / "crm" / CrmList / "synchronize") { list =>
        makeOperation("CrmSendList") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              makeOperation("SynchronizeList") { _ =>
                val startTime = System.currentTimeMillis()
                crmService
                  .synchronizeList(
                    DateHelper.now().toString,
                    list,
                    list.targetDirectory(mailJetConfiguration.csvDirectory)
                  )
                  .onComplete {
                    case Success(_) =>
                      logger
                        .info(
                          s"Synchronizing list ${list.value} succeeded in ${System.currentTimeMillis() - startTime}ms"
                        )
                    case Failure(e) =>
                      logger
                        .error(
                          s"Synchronizing list ${list.value} failed in ${System.currentTimeMillis() - startTime}ms",
                          e
                        )
                  }
                complete(StatusCodes.Accepted)
              }
            }
          }
        }
      }
    }

  }
}
