package org.make.api.proposal

import javax.ws.rs.Path

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller.CsvSeq
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.common.indexed.{Order, SortRequest}
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.{DateHelper, HttpCodes, Validation}

import scala.concurrent.Future
import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Proposal")
@Path(value = "/proposals")
trait ProposalApi extends MakeAuthenticationDirectives with StrictLogging {
  this: ProposalServiceComponent
    with ThemeServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with UserServiceComponent
    with OperationServiceComponent =>

  @ApiOperation(value = "get-proposal", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IndexedProposal]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getProposal: Route = {
    get {
      path("proposals" / proposalId) { proposalId =>
        makeTrace("GetProposal") { requestContext =>
          provideAsyncOrNotFound(proposalService.getProposalById(proposalId, requestContext)) { proposal =>
            complete(proposal)
          }
        }
      }
    }
  }

  @ApiOperation(value = "search-proposals", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultResponse]))
  )
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.proposal.SearchRequest"))
  )
  @Path(value = "/search")
  @Deprecated
  def searchDeprecated: Route = {
    post {
      path("proposals" / "search") {
        makeTrace("Search") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest {
              entity(as[SearchRequest]) { request: SearchRequest =>
                provideAsync(
                  proposalService
                    .searchForUser(
                      userId = userAuth.map(_.user.userId),
                      query = request.toSearchQuery(requestContext),
                      maybeSeed = request.randomScoreSeed,
                      requestContext = requestContext
                    )
                ) { proposals =>
                  complete(proposals)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "search-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "themesIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "labelsIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "trending", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "content", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "slug", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "seed", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "context", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "isRandom", paramType = "query", dataType = "boolean")
    )
  )
  def search: Route = {
    get {
      path("proposals") {
        makeTrace("Search") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters(
              (
                'proposalIds.as(CsvSeq[String]).?,
                'themesIds.as(CsvSeq[String]).?,
                'tagsIds.as(CsvSeq[String]).?,
                'labelsIds.as(CsvSeq[String]).?,
                'operationId.?,
                'trending.?,
                'content.?,
                'slug.?,
                'seed.as[Int].?,
                'source.?,
                'location.?,
                'question.?,
                'language.?,
                'country.?,
                'sort.?,
                'order.?,
                'limit.as[Int].?,
                'skip.as[Int].?,
                'isRandom.as[Boolean].?,
              )
            ) {
              (proposalIds,
               themesIds,
               tagsIds,
               labelsIds,
               operationId,
               trending,
               content,
               slug,
               seed,
               source,
               location,
               question,
               language,
               country,
               sort,
               order,
               limit,
               skip,
               isRandom) =>
                val contextFilterRequest: Option[ContextFilterRequest] =
                  operationId.orElse(source).orElse(location).orElse(question).map { _ =>
                    ContextFilterRequest(operationId.map(OperationId.apply), source, location, question)
                  }
                val sortRequest: Option[SortRequest] = sort.orElse(order).map { _ =>
                  SortRequest(sort, order.flatMap(Order.matchOrder))
                }
                val searchRequest: SearchRequest = SearchRequest(
                  proposalIds = proposalIds.map(_.map(ProposalId.apply)),
                  themesIds = themesIds.map(_.map(ThemeId.apply)),
                  tagsIds = tagsIds.map(_.map(TagId.apply)),
                  labelsIds = labelsIds.map(_.map(LabelId.apply)),
                  operationId = operationId.map(OperationId.apply),
                  trending = trending,
                  content = content,
                  slug = slug,
                  seed = seed,
                  context = contextFilterRequest,
                  language = language,
                  country = country,
                  sort = sortRequest
                )
                provideAsync(
                  proposalService
                    .searchForUser(
                      userId = userAuth.map(_.user.userId),
                      query = searchRequest.toSearchQuery(requestContext),
                      maybeSeed = searchRequest.randomScoreSeed,
                      requestContext = requestContext
                    )
                ) { proposals =>
                  complete(proposals)
                }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "propose-proposal",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.ProposeProposalRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposeProposalResponse]))
  )
  def postProposal: Route =
    post {
      path("proposals") {
        makeTrace("PostProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            decodeRequest {
              entity(as[ProposeProposalRequest]) { request: ProposeProposalRequest =>
                provideAsyncOrNotFound(userService.getUser(auth.user.userId)) { user =>
                  provideAsync(request.operationId match {
                    case Some(operationId) => operationService.findOne(operationId)
                    case None              => Future.successful(None)
                  }) { maybeOperation =>
                    request.operationId.foreach { _ =>
                      Validation.validate(
                        Validation.requirePresent("operationId", maybeOperation, Some("Invalid operationId"))
                      )
                    }
                    onSuccess(
                      proposalService
                        .propose(
                          user = user,
                          requestContext = requestContext,
                          createdAt = DateHelper.now(),
                          content = request.content,
                          operation = maybeOperation.map(_.operationId),
                          theme = requestContext.currentTheme,
                          country = request.country,
                          language = request.language
                        )
                    ) { proposalId =>
                      complete(StatusCodes.Created -> ProposeProposalResponse(proposalId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(value = "vote-proposal", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.VoteProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[VoteResponse])))
  @Path(value = "/{proposalId}/vote")
  def vote: Route = post {
    path("proposals" / proposalId / "vote") { proposalId =>
      makeTrace("VoteProposal") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
          decodeRequest {
            entity(as[VoteProposalRequest]) { request =>
              provideAsyncOrNotFound(
                proposalService.voteProposal(
                  proposalId = proposalId,
                  maybeUserId = maybeAuth.map(_.user.userId),
                  requestContext = requestContext,
                  voteKey = request.voteKey
                )
              ) { vote: Vote =>
                complete(VoteResponse.parseVote(vote = vote, hasVoted = true, None))
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "unvote-proposal", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.VoteProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[VoteResponse])))
  @Path(value = "/{proposalId}/unvote")
  def unvote: Route = post {
    path("proposals" / proposalId / "unvote") { proposalId =>
      makeTrace("UnvoteProposal") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
          decodeRequest {
            entity(as[VoteProposalRequest]) { request =>
              provideAsyncOrNotFound(
                proposalService.unvoteProposal(
                  proposalId = proposalId,
                  maybeUserId = maybeAuth.map(_.user.userId),
                  requestContext = requestContext,
                  voteKey = request.voteKey
                )
              ) { vote: Vote =>
                complete(VoteResponse.parseVote(vote = vote, hasVoted = false, None))
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "qualification-vote", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.QualificationProposalRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QualificationResponse]))
  )
  @Path(value = "/{proposalId}/qualification")
  def qualification: Route = post {
    path("proposals" / proposalId / "qualification") { proposalId =>
      makeTrace("QualificationProposal") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
          decodeRequest {
            entity(as[QualificationProposalRequest]) { request =>
              provideAsyncOrNotFound(
                proposalService.qualifyVote(
                  proposalId = proposalId,
                  maybeUserId = maybeAuth.map(_.user.userId),
                  requestContext = requestContext,
                  voteKey = request.voteKey,
                  qualificationKey = request.qualificationKey
                )
              ) { qualification: Qualification =>
                complete(QualificationResponse.parseQualification(qualification = qualification, hasQualified = true))
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "unqualification-vote", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.QualificationProposalRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QualificationResponse]))
  )
  @Path(value = "/{proposalId}/unqualification")
  def unqualification: Route = post {
    path("proposals" / proposalId / "unqualification") { proposalId =>
      makeTrace("UnqualificationProposal") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
          decodeRequest {
            entity(as[QualificationProposalRequest]) { request =>
              provideAsyncOrNotFound(
                proposalService.unqualifyVote(
                  proposalId = proposalId,
                  maybeUserId = maybeAuth.map(_.user.userId),
                  requestContext = requestContext,
                  voteKey = request.voteKey,
                  qualificationKey = request.qualificationKey
                )
              ) { qualification: Qualification =>
                complete(QualificationResponse.parseQualification(qualification = qualification, hasQualified = false))
              }
            }
          }
        }
      }
    }
  }

  val proposalRoutes: Route =
    postProposal ~
      getProposal ~
      searchDeprecated ~
      search ~
      vote ~
      unvote ~
      qualification ~
      unqualification

  val proposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)

}
