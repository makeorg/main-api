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

package org.make.api.proposal

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import cats.implicits._
import grizzled.slf4j.Logging
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.idea.IdeaServiceComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.proposal.indexed.{
  IndexedContext,
  IndexedProposal,
  IndexedQualification,
  IndexedTag,
  IndexedVote,
  ProposalElasticsearchFieldName
}
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, SearchQuery, VoteKey}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.technical.Pagination._
import org.make.core.user.{UserId, UserType}
import org.make.core.{CirceFormatters, DateHelper, HttpCodes, Order, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import java.time.ZonedDateTime
import scala.annotation.meta.field
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "AdminProposal")
@Path(value = "/admin/proposals")
trait AdminProposalApi extends Directives {

  @ApiOperation(
    value = "admin-search-proposals",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[AdminProposalResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(
        name = "status",
        paramType = "query",
        dataType = "string",
        allowableValues = "Pending,Accepted,Refused,Postponed,Archived",
        allowMultiple = true
      ),
      new ApiImplicitParam(name = "content", paramType = "query", dataType = "string"),
      new ApiImplicitParam(
        name = "userType",
        paramType = "query",
        dataType = "string",
        allowableValues = "USER,ORGANISATION,PERSONALITY",
        allowMultiple = true
      ),
      new ApiImplicitParam(name = "initialProposal", paramType = "query", dataType = "boolean"),
      new ApiImplicitParam(name = "tagId", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string")
    )
  )
  def search: Route

  @ApiOperation(
    value = "fix-trolled-proposal",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.UpdateProposalVotesRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @Path(value = "/{proposalId}/fix-trolled-proposal")
  def updateProposalVotes: Route

  @ApiOperation(
    value = "reset-unverified-proposal-votes",
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
  @Path(value = "/reset-votes")
  def resetVotes: Route

  @ApiOperation(
    value = "patch-proposal",
    httpMethod = "PATCH",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.proposal.PatchProposalRequest")
    )
  )
  @Path(value = "/{proposalId}")
  def patchProposal: Route

  @ApiOperation(
    value = "add-keywords",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ProposalKeywordsResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "[Lorg.make.api.operation.KeywordRequest;")
    )
  )
  @Path(value = "/keywords")
  def setProposalKeywords: Route

  @ApiOperation(
    value = "bulk-accept-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.BulkAcceptProposal")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BulkActionResponse]))
  )
  @Path(value = "/accept")
  def bulkAcceptProposal: Route

  @ApiOperation(
    value = "bulk-tag-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.BulkTagProposal")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BulkActionResponse]))
  )
  @Path(value = "/tag")
  def bulkTagProposal: Route

  @ApiOperation(
    value = "bulk-delete-tag-proposal",
    httpMethod = "DELETE",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.BulkDeleteTagProposal")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BulkActionResponse]))
  )
  @Path(value = "/tag")
  def bulkDeleteTagProposal: Route

  def routes: Route =
    search ~ patchProposal ~ updateProposalVotes ~ resetVotes ~ setProposalKeywords ~ bulkAcceptProposal ~ bulkTagProposal ~ bulkDeleteTagProposal
}

trait AdminProposalApiComponent {
  def adminProposalApi: AdminProposalApi
}

trait DefaultAdminProposalApiComponent
    extends AdminProposalApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with QuestionServiceComponent
    with IdeaServiceComponent
    with OperationServiceComponent
    with UserServiceComponent
    with TagServiceComponent =>

  override lazy val adminProposalApi: AdminProposalApi = new DefaultAdminProposalApi

  class DefaultAdminProposalApi extends AdminProposalApi {
    val adminProposalId: PathMatcher1[ProposalId] = Segment.map(id => ProposalId(id))

    override def search: Route = get {
      path("admin" / "proposals") {
        makeOperation("AdminSearchProposals") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              parameters(
                "questionId".csv[QuestionId],
                "content".?,
                "status".csv[ProposalStatus],
                "userType".csv[UserType],
                "initialProposal".as[Boolean].?,
                "tagId".csv[TagId],
                "_start".as[Start].?,
                "_end".as[End].?,
                "_sort".as[ProposalElasticsearchFieldName].?,
                "_order".as[Order].?
              ) {
                (
                  questionIds: Option[Seq[QuestionId]],
                  content: Option[String],
                  status: Option[Seq[ProposalStatus]],
                  userTypes: Option[Seq[UserType]],
                  initialProposal: Option[Boolean],
                  tagIds: Option[Seq[TagId]],
                  start: Option[Start],
                  end: Option[End],
                  sort: Option[ProposalElasticsearchFieldName],
                  order: Option[Order]
                ) =>
                  Validation.validate(sort.map { sortValue =>
                    val choices = ProposalElasticsearchFieldName.values.filter(_.sortable)
                    Validation.validChoices(
                      fieldName = "sort",
                      message = Some(
                        s"Invalid sort. Got $sortValue but expected one of: ${choices.map(_.parameter).mkString("\"", "\", \"", "\"")}"
                      ),
                      Seq(sortValue),
                      choices
                    )
                  }.toList: _*)

                  val exhaustiveSearchRequest: ExhaustiveSearchRequest = ExhaustiveSearchRequest(
                    initialProposal = initialProposal,
                    tagsIds = tagIds,
                    questionIds = questionIds,
                    content = content,
                    status = status,
                    sort = sort.map(_.field),
                    order = order,
                    limit = end.map(_.value),
                    skip = start.map(_.value),
                    userTypes = userTypes
                  )
                  val query: SearchQuery = exhaustiveSearchRequest.toSearchQuery(requestContext)
                  provideAsync(
                    proposalService
                      .search(userId = Some(userAuth.user.userId), query = query, requestContext = requestContext)
                  ) { proposals =>
                    complete(
                      (
                        StatusCodes.OK,
                        List(`X-Total-Count`(proposals.total.toString)),
                        proposals.results.map(AdminProposalResponse.apply)
                      )
                    )
                  }
              }
            }
          }
        }
      }
    }

    def updateProposalVotes: Route = put {
      path("admin" / "proposals" / adminProposalId / "fix-trolled-proposal") { proposalId =>
        makeOperation("EditProposalVotesVerified") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalVotesRequest]) { request =>
                  provideAsyncOrNotFound(retrieveProposalQuestion(proposalId)) { _ =>
                    provideAsyncOrNotFound(
                      proposalService.updateVotes(
                        proposalId = proposalId,
                        moderator = userAuth.user.userId,
                        requestContext = requestContext,
                        updatedAt = DateHelper.now(),
                        votesVerified = request.votes
                      )
                    ) { moderationProposalResponse: ModerationProposalResponse =>
                      complete(moderationProposalResponse)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def patchProposal: Route = {
      patch {
        path("admin" / "proposals" / adminProposalId) { id =>
          makeOperation("PatchProposal") { context =>
            makeOAuth2 { auth =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[PatchProposalRequest]) { patch =>
                    val author = patch.author.traverse(userService.getUser(_)).map(_.flatten)
                    val ideaId = patch.ideaId.traverse(ideaService.fetchOne(_)).map(_.flatten)
                    val operation = patch.operation.traverse(operationService.findOneSimple(_)).map(_.flatten)
                    val questionId = patch.questionId.traverse(questionService.getQuestion(_)).map(_.flatten)
                    val tags = patch.tags.traverse(tagService.findByTagIds(_).map(_.map(_.tagId)))

                    val validationValue = for {
                      maybeAuthorId    <- author
                      maybeIdeaId      <- ideaId
                      maybeOperationId <- operation
                      maybeQuestionId  <- questionId
                      maybeTagIds      <- tags
                    } yield {
                      Validation.validateOptional(
                        patch.author.map(
                          _ =>
                            Validation.validateField(
                              field = "author",
                              key = "invalid_value",
                              condition = maybeAuthorId.isDefined,
                              message = s"UserId ${patch.author} does not exist."
                            )
                        ),
                        patch.ideaId.map(
                          _ =>
                            Validation.validateField(
                              field = "ideaId",
                              key = "invalid_value",
                              condition = maybeIdeaId.isDefined,
                              message = s"Idea ${patch.ideaId} does not exist."
                            )
                        ),
                        patch.operation.map(
                          _ =>
                            Validation.validateField(
                              field = "operation",
                              key = "invalid_value",
                              condition = maybeOperationId.isDefined,
                              message = s"Operation ${patch.operation} does not exist."
                            )
                        ),
                        patch.questionId.map(
                          _ =>
                            Validation.validateField(
                              field = "questionId",
                              key = "invalid_value",
                              condition = maybeQuestionId.isDefined,
                              message = s"Question ${patch.questionId} does not exist."
                            )
                        )
                      )
                      maybeTagIds
                    }
                    provideAsync(validationValue) { maybeTagIds =>
                      provideAsyncOrNotFound(
                        proposalService.patchProposal(id, auth.user.userId, context, patch.copy(tags = maybeTagIds))
                      ) { proposal =>
                        complete(proposal)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    private def retrieveProposalQuestion(proposalId: ProposalId): Future[Option[Question]] = {
      proposalCoordinatorService.getProposal(proposalId).flatMap {
        case Some(proposal) =>
          proposal.questionId
            .map(questionService.getQuestion)
            .getOrElse(Future.successful(None))
        case None =>
          Future.successful(None)
      }
    }

    override def resetVotes: Route = post {
      path("admin" / "proposals" / "reset-votes") {
        withoutRequestTimeout {
          makeOperation("ResetVotes") { requestContext =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                proposalService.resetVotes(userAuth.user.userId, requestContext)
                complete(StatusCodes.Accepted)
              }
            }
          }
        }
      }
    }

    override def setProposalKeywords: Route = post {
      path("admin" / "proposals" / "keywords") {
        makeOperation("SetProposalKeywords") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[Seq[ProposalKeywordRequest]]) { request =>
                  provideAsync(proposalService.setKeywords(request, requestContext)) { response =>
                    complete(response)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def bulkAcceptProposal: Route = post {
      path("admin" / "proposals" / "accept") {
        makeOperation("BulkAcceptProposal") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[BulkAcceptProposal]) { request =>
                  provideAsync(proposalService.acceptAll(request.proposalIds, userAuth.user.userId, requestContext)) {
                    response =>
                      complete(response)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def bulkTagProposal: Route = post {
      path("admin" / "proposals" / "tag") {
        makeOperation("BulkTagProposal") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[BulkTagProposal]) { request =>
                  provideAsync(
                    proposalService
                      .addTagsToAll(request.proposalIds, request.tagIds, userAuth.user.userId, requestContext)
                  ) { response =>
                    complete(response)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def bulkDeleteTagProposal: Route = delete {
      path("admin" / "proposals" / "tag") {
        makeOperation("BulkDeleteTagProposal") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[BulkDeleteTagProposal]) { request =>
                  provideAsync(
                    proposalService
                      .deleteTagFromAll(request.proposalIds, request.tagId, userAuth.user.userId, requestContext)
                  ) { response =>
                    complete(response)
                  }
                }
              }
            }
          }
        }
      }
    }

  }

}

final case class AdminProposalResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b") id: ProposalId,
  author: AdminProposalResponse.Author,
  content: String,
  @(ApiModelProperty @field)(dataType = "string", example = "Pending") status: ProposalStatus,
  tags: Seq[IndexedTag] = Seq.empty,
  createdAt: ZonedDateTime,
  agreementRate: Double,
  context: AdminProposalResponse.Context,
  votes: Seq[AdminProposalResponse.Vote],
  votesCount: Int
)

object AdminProposalResponse extends CirceFormatters {

  def apply(proposal: IndexedProposal): AdminProposalResponse =
    AdminProposalResponse(
      id = proposal.id,
      author = Author(proposal),
      content = proposal.content,
      status = proposal.status,
      tags = proposal.tags,
      createdAt = proposal.createdAt,
      agreementRate = proposal.scores.agreement,
      context = Context(proposal.context),
      votes = proposal.votes.map(Vote.apply),
      votesCount = proposal.votesCount
    )

  final case class Author(
    @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e") id: UserId,
    @(ApiModelProperty @field)(dataType = "string", example = "USER") userType: UserType,
    displayName: Option[String],
    postalCode: Option[String],
    @(ApiModelProperty @field)(dataType = "integer", example = "42") age: Option[Int],
    profession: Option[String]
  )

  object Author {
    def apply(proposal: IndexedProposal): Author =
      Author(
        id = proposal.userId,
        userType = proposal.author.userType,
        displayName = proposal.author.displayName,
        postalCode = proposal.author.postalCode,
        age = proposal.author.age,
        profession = proposal.author.profession
      )
    implicit val codec: Codec[Author] = deriveCodec
  }

  final case class Context(
    source: Option[String],
    question: Option[String],
    @(ApiModelProperty @field)(dataType = "string", example = "FR") country: Option[Country],
    @(ApiModelProperty @field)(dataType = "string", example = "fr") language: Option[Language]
  )

  object Context {
    def apply(context: Option[IndexedContext]): Context =
      Context(
        source = context.flatMap(_.source),
        question = context.flatMap(_.question),
        country = context.flatMap(_.country),
        language = context.flatMap(_.language)
      )
    implicit val codec: Codec[Context] = deriveCodec
  }

  final case class Vote(
    @(ApiModelProperty @field)(dataType = "string", example = "agree")
    key: VoteKey,
    count: Int,
    qualifications: Seq[Qualification]
  )

  object Vote {
    def apply(vote: IndexedVote): Vote =
      Vote(key = vote.key, count = vote.count, qualifications = vote.qualifications.map(Qualification.apply))
    implicit val codec: Codec[Vote] = deriveCodec
  }

  final case class Qualification(
    @(ApiModelProperty @field)(dataType = "string", example = "LikeIt")
    key: QualificationKey,
    count: Int
  )

  object Qualification {
    def apply(qualification: IndexedQualification): Qualification =
      Qualification(key = qualification.key, count = qualification.count)
    implicit val codec: Codec[Qualification] = deriveCodec
  }

  implicit val codec: Codec[AdminProposalResponse] = deriveCodec

}
