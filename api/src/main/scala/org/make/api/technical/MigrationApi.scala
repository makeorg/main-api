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

package org.make.api.technical

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.{Authorization, _}
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.operation.OperationOfQuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.crm.CrmServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.operation.OperationOfQuestion
import org.make.core.tag.{Tag => _}
import org.make.core.{CirceFormatters, DateHelper, HttpCodes, Validation}

import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "delete-mailjet-anoned-contacts",
    httpMethod = "POST",
    code = HttpCodes.OK,
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
        value = "body",
        paramType = "body",
        dataType = "org.make.api.technical.DeleteContactsRequest"
      )
    )
  )
  @Path(value = "/delete-mailjet-anoned-contacts")
  def deleteMailjetAnonedContacts: Route

  @ApiOperation(
    value = "set-proper-signup-operation",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "NoContent")))
  @Path(value = "/set-proper-signup-operation")
  def setProperSignUpOperation: Route

  @ApiOperation(
    value = "count-damage-user-account",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "OK", response = classOf[Int])))
  @Path(value = "/count-damage-user-account")
  def countDamageUserAccount: Route

  def routes: Route = deleteMailjetAnonedContacts ~ setProperSignUpOperation ~ countDamageUserAccount
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ActorSystemComponent
    with SessionHistoryCoordinatorServiceComponent
    with CrmServiceComponent
    with MailJetConfigurationComponent
    with OperationOfQuestionServiceComponent
    with UserServiceComponent =>

  override lazy val migrationApi: MigrationApi = new DefaultMigrationApi

  implicit private lazy val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

  class DefaultMigrationApi extends MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def deleteMailjetAnonedContacts: Route = post {
      path("migrations" / "delete-mailjet-anoned-contacts") {
        withoutRequestTimeout {
          makeOperation("DeleteMailjetAnonedContacts") { _ =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[DeleteContactsRequest]) { req =>
                    crmService.deleteAllContactsBefore(req.maxUpdatedAtBeforeDelete, req.deleteEmptyProperties)
                    complete(StatusCodes.Accepted)
                  }
                }
              }
            }
          }
        }
      }
    }

    private def isValidDate(registerDate: ZonedDateTime,
                            startDate: Option[ZonedDateTime],
                            endDate: Option[ZonedDateTime]): Boolean = {
      startDate.forall(date => registerDate.isAfter(date)) && endDate.forall(date => registerDate.isBefore(date))
    }

    override def countDamageUserAccount: Route = get {
      path("migrations" / "count-damage-user-account") {
        withoutRequestTimeout {
          makeOperation("CountDamageUserAccount") { _ =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                provideAsync(operationOfQuestionService.find()) { questions =>
                  StreamUtils
                    .asyncPageToPageSource(
                      offset =>
                        userService.adminFindUsers(
                          start = offset,
                          end = Some(mailJetConfiguration.userListBatchSize),
                          None,
                          None,
                          None,
                          None,
                          None
                      )
                    )
                    .mapConcat(_.toVector)
                    .filterNot(user => user.email.startsWith("yopmail"))
                    .mapAsync(1) { user =>
                      val registerQuestion: Option[OperationOfQuestion] = questions
                        .find(question => user.profile.flatMap(_.registerQuestionId).contains(question.questionId))
                      (user.createdAt, registerQuestion) match {
                        case (Some(date), Some(question)) if !isValidDate(date, question.startDate, question.endDate) =>
                          Future.successful(1)
                        case _ => Future.successful(0)
                      }
                    }
                    .runFold(0)(_ + _)
                    .onComplete {
                      case Success(count) => logger.info(s"$count user accounts to be updated")
                      case Failure(e)     => logger.error("Count damage user accounts error", e)
                    }
                  complete(StatusCodes.Accepted)
                }
              }
            }
          }
        }
      }
    }

    override def setProperSignUpOperation: Route = post {
      path("migrations" / "set-proper-signup-operation") {
        withoutRequestTimeout {
          makeOperation("SetProperSignUpOperation") { requestContext =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                operationOfQuestionService.find().map { questions =>
                  StreamUtils
                    .asyncPageToPageSource(
                      offset =>
                        userService.adminFindUsers(
                          start = offset,
                          end = Some(mailJetConfiguration.userListBatchSize),
                          None,
                          None,
                          None,
                          None,
                          None
                      )
                    )
                    .mapConcat(_.toVector)
                    .filterNot(user => user.email.startsWith("yopmail"))
                    .mapAsync(1) { user =>
                      val registerQuestion: Option[OperationOfQuestion] = questions
                        .find(question => user.profile.flatMap(_.registerQuestionId).contains(question.questionId))
                      (user.createdAt, registerQuestion) match {
                        case (Some(date), Some(question)) if !isValidDate(date, question.startDate, question.endDate) =>
                          userService
                            .update(
                              user.copy(profile = user.profile.map(_.copy(registerQuestionId = None))),
                              requestContext
                            )
                            .map(_ => 1)
                        case _ => Future.successful(0)
                      }
                    }
                    .runFold(0)(_ + _)
                    .onComplete {
                      case Success(count) => logger.info(s"$count user accounts were successfully updated")
                      case Failure(e)     => logger.error("Update damage user accounts error", e)
                    }
                }
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }

  }

}

final case class DeleteContactsRequest(@(ApiModelProperty @field)(
                                         dataType = "string",
                                         example = "2019-07-11T11:21:40.508Z"
                                       ) maxUpdatedAtBeforeDelete: ZonedDateTime,
                                       @(ApiModelProperty @field)(dataType = "boolean") deleteEmptyProperties: Boolean) {
  Validation.validate(
    Validation.validateField(
      "maxUpdatedAtBeforeDelete",
      "invalid_date",
      maxUpdatedAtBeforeDelete.isBefore(DateHelper.now().minusDays(1)),
      "DeleteFor cannot be set to a date more recent than yesterday."
    )
  )
}

object DeleteContactsRequest extends CirceFormatters {
  implicit val decoder: Decoder[DeleteContactsRequest] = deriveDecoder[DeleteContactsRequest]

}
