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

package org.make.api.sequence

import akka.http.scaladsl.server._
import eu.timepit.refined.auto._
import grizzled.slf4j.Logging
import io.swagger.annotations._
import org.make.api.keyword.KeywordServiceComponent
import org.make.api.operation.OperationOfQuestionSearchEngineComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.sequence.SequenceBehaviour.ConsensusParam
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.core.auth.UserRights
import org.make.core.demographics.DemographicsCardId
import org.make.core.proposal.indexed.Zone
import org.make.core.proposal.{ProposalId, ProposalKeywordKey}
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceConfiguration
import org.make.core.user.{CountrySearchFilter => _, DescriptionSearchFilter => _, LanguageSearchFilter => _}
import org.make.core.{ApplicationName, HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SequenceApiComponent {
  def sequenceApi: SequenceApi
}

@Api(value = "Sequences")
@Path(value = "/sequences")
trait SequenceApi extends Directives {

  @ApiOperation(value = "start-standard-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "demographicsCardId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "token", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResult])))
  @Path(value = "/standard/{questionId}")
  def startStandardSequence: Route

  @ApiOperation(value = "start-consensus-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "demographicsCardId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "token", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResult])))
  @Path(value = "/consensus/{questionId}")
  def startConsensusSequence: Route

  @ApiOperation(value = "start-controversy-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "demographicsCardId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "token", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResult])))
  @Path(value = "/controversy/{questionId}")
  def startControversySequence: Route

  @ApiOperation(value = "start-keyword-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "keywordKey", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "demographicsCardId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "token", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[KeywordSequenceResult]))
  )
  @Path(value = "/keyword/{questionId}/{keywordKey}")
  def startKeywordSequence: Route

  @ApiOperation(value = "get-standard-first-proposal", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FirstProposalResponse]))
  )
  @Path(value = "/standard/{questionId}/first-proposal")
  def getStandardFirstProposal: Route

  def routes: Route =
    startStandardSequence ~ startConsensusSequence ~ startControversySequence ~ startKeywordSequence ~ getStandardFirstProposal
}

trait DefaultSequenceApiComponent extends SequenceApiComponent {

  this: MakeDirectivesDependencies
    with SequenceServiceComponent
    with SequenceCacheManagerServiceComponent
    with SequenceConfigurationComponent
    with OperationOfQuestionSearchEngineComponent
    with QuestionServiceComponent
    with KeywordServiceComponent =>

  override lazy val sequenceApi: SequenceApi = new DefaultSequenceApi

  class DefaultSequenceApi extends SequenceApi with Logging with ParameterExtractors {

    private val questionId: PathMatcher1[QuestionId] = Segment.map(QuestionId.apply)
    private val keywordKey: PathMatcher1[ProposalKeywordKey] = Segment.map(ProposalKeywordKey.apply)

    override def startStandardSequence: Route = get {
      path("sequences" / "standard" / questionId) { questionId =>
        makeOperation("StartStandardSequence") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters("include".csv[ProposalId], "demographicsCardId".as[DemographicsCardId].?, "token".?) {
              (includes: Option[Seq[ProposalId]], cardId: Option[DemographicsCardId], token: Option[String]) =>
                provideAsyncOrNotFound(questionService.getCachedQuestion(questionId)) { question =>
                  // TODO: remove the following once "mafrance2022" consultation ends.
                  val futureConfigurationOverride: Future[Option[SequenceConfiguration]] = {
                    if (question.slug == "mafrance2022" && requestContext.applicationName
                          .contains(ApplicationName.Widget)) {
                      sequenceConfigurationService
                        .getSequenceConfigurationByQuestionId(questionId)
                        .map(config => Some(config.copy(mainSequence = config.mainSequence.copy(sequenceSize = 12))))
                    } else {
                      Future.successful(None)
                    }
                  }

                  provideAsync(futureConfigurationOverride) { configurationOverride =>
                    provideAsync(
                      sequenceService
                        .startNewSequence(
                          behaviourParam = (),
                          maybeUserId = userAuth.map(_.user.userId),
                          questionId = questionId,
                          includedProposalsIds = includes.getOrElse(Seq.empty),
                          requestContext = requestContext,
                          cardId = cardId,
                          token = token,
                          configurationOverride = configurationOverride
                        )
                    ) { sequenceResult =>
                      complete(sequenceResult)
                    }
                  }
                }
            }
          }
        }
      }
    }

    override def startConsensusSequence: Route = get {
      path("sequences" / "consensus" / questionId) { questionId =>
        makeOperation("StartConsensusSequence") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters("include".csv[ProposalId], "demographicsCardId".as[DemographicsCardId].?, "token".?) {
              (includes: Option[Seq[ProposalId]], cardId: Option[DemographicsCardId], token: Option[String]) =>
                val futureTop20ConsensusThreshold: Future[Option[Double]] =
                  elasticsearchOperationOfQuestionAPI
                    .findOperationOfQuestionById(questionId)
                    .map(_.flatMap(_.top20ConsensusThreshold))
                provideAsyncOrNotFound(questionService.getCachedQuestion(questionId)) { _ =>
                  provideAsync(futureTop20ConsensusThreshold) { threshold =>
                    provideAsync(
                      sequenceService
                        .startNewSequence(
                          behaviourParam = ConsensusParam(threshold),
                          maybeUserId = userAuth.map(_.user.userId),
                          questionId = questionId,
                          includedProposalsIds = includes.getOrElse(Seq.empty),
                          requestContext = requestContext,
                          cardId = cardId,
                          token = token
                        )
                    ) { sequenceResult =>
                      complete(sequenceResult)
                    }
                  }
                }
            }
          }
        }
      }
    }

    override def startControversySequence: Route = get {
      path("sequences" / "controversy" / questionId) { questionId =>
        makeOperation("StartControversySequence") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters("include".csv[ProposalId], "demographicsCardId".as[DemographicsCardId].?, "token".?) {
              (includes: Option[Seq[ProposalId]], cardId: Option[DemographicsCardId], token: Option[String]) =>
                val futureQuestion = questionService.getCachedQuestion(questionId)
                val futureSequence = sequenceService
                  .startNewSequence(
                    behaviourParam = Zone.Controversy,
                    maybeUserId = userAuth.map(_.user.userId),
                    questionId = questionId,
                    includedProposalsIds = includes.getOrElse(Seq.empty),
                    requestContext = requestContext,
                    cardId = cardId,
                    token = token
                  )
                provideAsyncOrNotFound(futureQuestion) { _ =>
                  provideAsync(futureSequence) { sequenceResult =>
                    complete(sequenceResult)
                  }
                }
            }
          }
        }
      }
    }

    override def startKeywordSequence: Route = get {
      path("sequences" / "keyword" / questionId / keywordKey) { (questionId, keywordKey) =>
        makeOperation("StartKeywordSequence") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters("include".csv[ProposalId], "demographicsCardId".as[DemographicsCardId].?, "token".?) {
              (includes: Option[Seq[ProposalId]], cardId: Option[DemographicsCardId], token: Option[String]) =>
                val futureQuestion = questionService.getCachedQuestion(questionId)
                val futureKeyword = keywordService.get(keywordKey.value, questionId)
                provideAsyncOrNotFound(futureQuestion) { _ =>
                  provideAsyncOrNotFound(futureKeyword) { keyword =>
                    provideAsync(
                      sequenceService
                        .startNewSequence(
                          behaviourParam = keywordKey,
                          maybeUserId = userAuth.map(_.user.userId),
                          questionId = questionId,
                          includedProposalsIds = includes.getOrElse(Seq.empty),
                          requestContext = requestContext,
                          cardId = cardId,
                          token = token
                        )
                    ) { sequenceResult =>
                      complete(
                        KeywordSequenceResult(
                          key = keyword.key,
                          label = keyword.label,
                          proposals = sequenceResult.proposals,
                          demographics = sequenceResult.demographics
                        )
                      )
                    }
                  }
                }
            }
          }
        }
      }
    }

    override def getStandardFirstProposal: Route = get {
      path("sequences" / "standard" / questionId / "first-proposal") { questionId =>
        makeOperation("GetStandardFirstProposal") { requestContext =>
          provideAsyncOrNotFound(questionService.getCachedQuestion(questionId)) { _ =>
            provideAsync(sequenceConfigurationService.getSequenceConfigurationByQuestionId(questionId)) { config =>
              provideAsync(sequenceCacheManagerService.getProposal(questionId, requestContext)) { proposalResponse =>
                complete(FirstProposalResponse(proposalResponse, config.mainSequence.sequenceSize))
              }
            }
          }
        }
      }
    }
  }
}
