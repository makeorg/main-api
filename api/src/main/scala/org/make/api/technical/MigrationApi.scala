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

import java.util.concurrent.Executors

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.{IdeaFiltersRequest, IdeaServiceComponent, PersistentIdeaServiceComponent}
import org.make.api.operation.OperationServiceComponent
import org.make.api.proposal.{PatchProposalCommand, PatchProposalRequest, ProposalCoordinatorServiceComponent}
import org.make.api.question.PersistentQuestionServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.{tag, HttpCodes}
import org.make.core.auth.UserRights
import org.make.core.idea.Idea
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.{Tag => _}
import scalaoauth2.provider.AuthInfo

import scala.collection.generic.CanBuildFrom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends MakeAuthenticationDirectives with StrictLogging {
  self: OperationServiceComponent
    with PersistentQuestionServiceComponent
    with ReadJournalComponent
    with MakeDataHandlerComponent
    with ActorSystemComponent
    with TagServiceComponent
    with PersistentIdeaServiceComponent
    with IdeaServiceComponent
    with ProposalCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent =>

  @Path(value = "/question/attach-ideas")
  @ApiOperation(
    value = "attach-idea-to-question",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Unit])))
  def attachIdeasToQuestion: Route = {
    post {
      path("migrations" / "question" / "attach-ideas") {
        makeOperation("AttachIdeasToQuestion") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsync(persistentQuestionService.find(None, None, None, None)) { questions =>
                provideAsync(persistentIdeaService.findAll(IdeaFiltersRequest(None, None, None, None, None, None))) {
                  ideas =>
                    val modifyIdeas = Future.traverse(ideas) { idea =>
                      val maybeQuestion = questions
                        .find(
                          question =>
                            question.operationId == idea.operationId &&
                              question.themeId == idea.themeId &&
                              question.country == idea.country.getOrElse(Country("FR")) &&
                              question.language == idea.language.getOrElse(Language("fr"))
                        )
                      maybeQuestion.map { question =>
                        val newIdea = idea.copy(questionId = Some(question.questionId))
                        persistentIdeaService
                          .updateIdea(newIdea)
                          .map(_ => Some(newIdea))
                      }.getOrElse {
                        logger.warn(s"No suitable question for idea $idea")
                        Future.successful(None)
                      }
                    }(
                      executor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5)),
                      cbf = implicitly[CanBuildFrom[Seq[Idea], Option[Idea], Seq[Option[Idea]]]]
                    )
                    onComplete(modifyIdeas) { _ =>
                      complete(StatusCodes.OK)
                    }
                }
              }
            }
          }
        }
      }
    }
  }

  @Path(value = "/question/attach-tags")
  @ApiOperation(
    value = "attach-tags-to-question",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Unit])))
  def attachTagsToQuestion: Route = {
    post {
      path("migrations" / "question" / "attach-tags") {
        makeOperation("AttachTagsToQuestion") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsync(persistentQuestionService.find(None, None, None, None)) { questions =>
                provideAsync(tagService.findAll()) { tags =>
                  val modifyTags = Future.traverse(tags) { tag =>
                    val maybeQuestion = questions
                      .find(
                        question =>
                          question.operationId == tag.operationId &&
                            question.themeId == tag.themeId &&
                            question.country == tag.country &&
                            question.language == tag.language
                      )
                    maybeQuestion.map { question =>
                      tagService.updateTag(
                        tag.tagId,
                        tag.label,
                        tag.display,
                        tag.tagTypeId,
                        tag.weight,
                        question,
                        requestContext
                      )
                    }.getOrElse {
                      logger.warn(s"No suitable question for tag $tag")
                      Future.successful(None)
                    }
                  }(
                    executor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5)),
                    cbf = implicitly[CanBuildFrom[Seq[tag.Tag], Option[tag.Tag], Seq[Option[tag.Tag]]]]
                  )
                  onComplete(modifyTags) { _ =>
                    complete(StatusCodes.OK)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @Path(value = "/question/attach-proposals")
  @ApiOperation(
    value = "attach-proposals-to-question",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Unit])))
  def attachProposalsToQuestion: Route = {
    post {
      path("migrations" / "question" / "attach-proposals") {
        makeOperation("AttachProposalsToQuestion") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsync(persistentQuestionService.find(None, None, None, None)) { questions =>
                implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

                def findQuestion(maybeThemeId: Option[ThemeId],
                                 maybeOperationId: Option[OperationId],
                                 maybeCountry: Option[Country]): Option[QuestionId] = {
                  val themesMap: Map[ThemeId, QuestionId] =
                    questions
                      .filter(_.themeId.isDefined)
                      .map(question => question.themeId.get -> question.questionId)
                      .toMap

                  val operationMap: Map[(OperationId, Country), QuestionId] =
                    questions
                      .filter(_.operationId.isDefined)
                      .map(question => (question.operationId.get, question.country) -> question.questionId)
                      .toMap

                  maybeThemeId.flatMap { themeId =>
                    themesMap.get(themeId)
                  }.orElse {
                    for {
                      country    <- maybeCountry
                      operation  <- maybeOperationId
                      questionId <- operationMap.get((operation, country))
                    } yield {
                      questionId
                    }
                  }
                }

                proposalJournal
                  .currentPersistenceIds()
                  .mapAsync(5)(
                    id =>
                      proposalCoordinatorService.getProposal(ProposalId(id)).map {
                        case None =>
                          logger.warn(s"Proposal $id was not found")
                          None
                        case other => other
                    }
                  )
                  .map {
                    case Some(proposal) =>
                      val maybeQuestionId = findQuestion(proposal.theme, proposal.operation, proposal.country)
                      if (maybeQuestionId.isDefined) {
                        logger.debug(
                          s"Assigning question ${maybeQuestionId.get.value} to proposal ${proposal.proposalId}"
                        )
                        proposalCoordinatorService.patch(
                          PatchProposalCommand(
                            proposal.proposalId,
                            userAuth.user.userId,
                            PatchProposalRequest(questionId = maybeQuestionId),
                            requestContext
                          )
                        )
                      } else {
                        logger.warn(s"No question for proposal ${proposal.toString}")
                      }
                      Done
                    case None =>
                      Done
                  }
                  .runForeach(_ => ())
                  .onComplete {
                    case Success(_) => logger.info("Question migration completed")
                    case _          => logger.info("Question migration failed")
                  }

                complete(StatusCodes.OK)
              }
            }
          }
        }
      }
    }
  }

  def dummy: Route = {
    get {
      path("migrations") {
        complete(StatusCodes.OK)
      }
    }
  }

  val migrationRoutes: Route = attachProposalsToQuestion ~ attachTagsToQuestion ~ attachIdeasToQuestion
}
