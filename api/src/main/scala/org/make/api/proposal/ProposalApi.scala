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

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller.CsvSeq
import grizzled.slf4j.Logging
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.{RequestSessionVoteValues, RequestSessionVotedProposals}
import org.make.api.technical.MakeAuthenticationDirectives
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.user.UserServiceComponent
import org.make.core.Order
import org.make.core.auth.UserRights
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal._
import org.make.core.proposal.indexed.ProposalElasticsearchFieldName
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserType
import org.make.core.{
  BusinessConfig,
  DateHelper,
  HttpCodes,
  ParameterExtractors,
  Validation,
  ValidationError,
  ValidationFailedError
}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "Proposal")
@Path(value = "/proposals")
trait ProposalApi extends Directives {

  @ApiOperation(value = "get-proposal", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getProposal: Route

  @ApiOperation(value = "search-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "content", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "slug", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "seed", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "source", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "context", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "location", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "question", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "isRandom", paramType = "query", dataType = "boolean"),
      new ApiImplicitParam(
        name = "sortAlgorithm",
        paramType = "query",
        dataType = "string",
        allowableValues = "random,taggedFirst,actorVote,controversy,popular,B2BFirst"
      ),
      new ApiImplicitParam(
        name = "operationKinds",
        paramType = "query",
        dataType = "string",
        allowableValues = "GREAT_CAUSE,PRIVATE_CONSULTATION,BUSINESS_CONSULTATION",
        allowMultiple = true
      ),
      new ApiImplicitParam(
        name = "userType",
        paramType = "query",
        dataType = "string",
        allowableValues = "USER,ORGANISATION,PERSONALITY"
      ),
      new ApiImplicitParam(name = "ideaIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "keywords", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "isNotVoted", paramType = "query", dataType = "boolean")
    )
  )
  def search: Route

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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalIdResponse]))
  )
  def postProposal: Route

  @ApiOperation(value = "vote-proposal", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.VoteProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[VoteResponse])))
  @Path(value = "/{proposalId}/vote")
  def vote: Route

  @ApiOperation(value = "unvote-proposal", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.VoteProposalRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[VoteResponse])))
  @Path(value = "/{proposalId}/unvote")
  def unvote: Route

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
  def qualification: Route

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
  def unqualification: Route

  def routes: Route =
    postProposal ~
      getProposal ~
      search ~
      vote ~
      unvote ~
      qualification ~
      unqualification

}

trait ProposalApiComponent {
  def proposalApi: ProposalApi
}

trait DefaultProposalApiComponent
    extends ProposalApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies
    with ProposalServiceComponent
    with UserServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent =>

  override lazy val proposalApi: DefaultProposalApi = new DefaultProposalApi

  class DefaultProposalApi extends ProposalApi {
    val proposalId: PathMatcher1[ProposalId] =
      Segment.map(id => ProposalId(id))

    def getProposal: Route = {
      get {
        path("proposals" / proposalId) { proposalId =>
          makeOperation("GetProposal") { requestContext =>
            provideAsyncOrNotFound(proposalService.getProposalById(proposalId, requestContext)) { proposal =>
              provideAsync(
                sessionHistoryCoordinatorService
                  .retrieveVoteAndQualifications(RequestSessionVoteValues(requestContext.sessionId, Seq(proposalId)))
              ) { votes =>
                val proposalKey =
                  proposalService.generateProposalKeyHash(
                    proposalId,
                    requestContext.sessionId,
                    requestContext.location,
                    securityConfiguration.secureVoteSalt
                  )
                complete(
                  ProposalResponse(
                    proposal,
                    requestContext.userId.contains(proposal.userId),
                    votes.get(proposalId),
                    proposalKey
                  )
                )
              }
            }
          }
        }
      }
    }

    def search: Route = {
      get {
        path("proposals") {
          makeOperation("Search") { requestContext =>
            optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
              parameters(
                "proposalIds".as[Seq[ProposalId]].?,
                "questionId".as[Seq[QuestionId]].?,
                "tagsIds".as[Seq[TagId]].?,
                "operationId".as[OperationId].?,
                "content".?,
                "slug".?,
                "source".?,
                "location".?,
                "question".?,
                "language".as[Language].?,
                "country".as[Country].?,
                "operationKinds".csv[OperationKind],
                "userType".as[Seq[UserType]].?,
                "ideaIds".as[Seq[IdeaId]].?,
                "keywords".as[Seq[ProposalKeywordKey]].?,
                "isNotVoted".as[Boolean].?
              ) {
                (
                  proposalIds: Option[Seq[ProposalId]],
                  questionIds: Option[Seq[QuestionId]],
                  tagsIds: Option[Seq[TagId]],
                  operationId: Option[OperationId],
                  content: Option[String],
                  slug: Option[String],
                  source: Option[String],
                  location: Option[String],
                  question: Option[String],
                  language: Option[Language],
                  country: Option[Country],
                  operationKinds: Option[Seq[OperationKind]],
                  userType: Option[Seq[UserType]],
                  ideaIds: Option[Seq[IdeaId]],
                  keywords: Option[Seq[ProposalKeywordKey]],
                  isNotVoted: Option[Boolean]
                ) =>
                  parameters(
                    "sort".as[ProposalElasticsearchFieldName].?,
                    "order".as[Order].?,
                    "limit".as[Int].?,
                    "skip".as[Int].?,
                    "sortAlgorithm".?,
                    "seed".as[Int].?
                  ) {
                    (
                      sort: Option[ProposalElasticsearchFieldName],
                      order: Option[Order],
                      limit: Option[Int],
                      skip: Option[Int],
                      sortAlgorithm: Option[String],
                      seed: Option[Int]
                    ) =>
                      Validation.validate(Seq(country.map { countryValue =>
                        Validation.validChoices(
                          fieldName = "country",
                          message = Some(
                            s"Invalid country. Expected one of ${BusinessConfig.supportedCountries.map(_.countryCode)}"
                          ),
                          Seq(countryValue),
                          BusinessConfig.supportedCountries.map(_.countryCode)
                        )
                      }, sort.map(Validation.validateSort("sort")), sortAlgorithm.map { sortAlgo =>
                        Validation.validChoices(
                          fieldName = "sortAlgorithm",
                          message = Some(s"Invalid algorithm. Expected one of: ${AlgorithmSelector.keys}"),
                          Seq(sortAlgo),
                          AlgorithmSelector.keys
                        )
                      }).flatten: _*)

                      val futureExcludedProposalIds = isNotVoted match {
                        case Some(true) =>
                          sessionHistoryCoordinatorService
                            .retrieveVotedProposals(RequestSessionVotedProposals(requestContext.sessionId))
                            .map {
                              case Seq() => None
                              case voted => Some(voted)
                            }
                        case _ => Future.successful(None)
                      }
                      provideAsync(futureExcludedProposalIds) { excludedProposalIds =>
                        val contextFilterRequest: Option[ContextFilterRequest] =
                          ContextFilterRequest.parse(operationId, source, location, question)
                        val searchRequest: SearchRequest = SearchRequest(
                          proposalIds = proposalIds,
                          questionIds = questionIds,
                          tagsIds = tagsIds,
                          operationId = operationId,
                          content = content,
                          slug = slug,
                          seed = seed,
                          context = contextFilterRequest,
                          language = language,
                          country = country,
                          sort = sort.map(_.field),
                          order = order,
                          limit = limit,
                          skip = skip,
                          sortAlgorithm = sortAlgorithm,
                          operationKinds = operationKinds.orElse {
                            if (questionIds.exists(_.nonEmpty)) {
                              None
                            } else {
                              Some(OperationKind.publicKinds)
                            }
                          },
                          userTypes = userType,
                          ideaIds = ideaIds,
                          keywords = keywords,
                          excludedProposalIds = excludedProposalIds
                        )
                        provideAsync(
                          proposalService
                            .searchForUser(
                              userId = userAuth.map(_.user.userId),
                              query = searchRequest.toSearchQuery(requestContext),
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
    }

    def postProposal: Route =
      post {
        path("proposals") {
          makeOperation("PostProposal") { requestContext =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              decodeRequest {
                entity(as[ProposeProposalRequest]) { request: ProposeProposalRequest =>
                  provideAsyncOrNotFound(userService.getUser(auth.user.userId)) { user =>
                    provideAsync(questionService.getQuestion(request.questionId).flatMap {
                      case Some(question) => Future.successful(question)
                      case _ =>
                        Future.failed(
                          ValidationFailedError(
                            Seq(
                              ValidationError(
                                "question",
                                "mandatory",
                                Some("This proposal refers to no known question")
                              )
                            )
                          )
                        )
                    }) { question =>
                      onSuccess(
                        proposalService
                          .propose(
                            user = user,
                            requestContext = requestContext,
                            createdAt = DateHelper.now(),
                            content = request.content,
                            question = question,
                            initialProposal = false
                          )
                      ) { proposalId =>
                        complete(StatusCodes.Created -> ProposalIdResponse(proposalId))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

    def vote: Route = post {
      path("proposals" / proposalId / "vote") { proposalId =>
        makeOperation("VoteProposal") { requestContext =>
          optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest {
              entity(as[VoteProposalRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.voteProposal(
                    proposalId = proposalId,
                    maybeUserId = maybeAuth.map(_.user.userId),
                    requestContext = requestContext,
                    voteKey = request.voteKey,
                    proposalKey = request.proposalKey
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

    def unvote: Route = post {
      path("proposals" / proposalId / "unvote") { proposalId =>
        makeOperation("UnvoteProposal") { requestContext =>
          optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest {
              entity(as[VoteProposalRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.unvoteProposal(
                    proposalId = proposalId,
                    maybeUserId = maybeAuth.map(_.user.userId),
                    requestContext = requestContext,
                    voteKey = request.voteKey,
                    proposalKey = request.proposalKey
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

    def qualification: Route = post {
      path("proposals" / proposalId / "qualification") { proposalId =>
        makeOperation("QualificationProposal") { requestContext =>
          optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest {
              entity(as[QualificationProposalRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.qualifyVote(
                    proposalId = proposalId,
                    maybeUserId = maybeAuth.map(_.user.userId),
                    requestContext = requestContext,
                    voteKey = request.voteKey,
                    qualificationKey = request.qualificationKey,
                    proposalKey = request.proposalKey
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

    def unqualification: Route = post {
      path("proposals" / proposalId / "unqualification") { proposalId =>
        makeOperation("UnqualificationProposal") { requestContext =>
          optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest {
              entity(as[QualificationProposalRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.unqualifyVote(
                    proposalId = proposalId,
                    maybeUserId = maybeAuth.map(_.user.userId),
                    requestContext = requestContext,
                    voteKey = request.voteKey,
                    qualificationKey = request.qualificationKey,
                    proposalKey = request.proposalKey
                  )
                ) { qualification: Qualification =>
                  complete(
                    QualificationResponse.parseQualification(qualification = qualification, hasQualified = false)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
