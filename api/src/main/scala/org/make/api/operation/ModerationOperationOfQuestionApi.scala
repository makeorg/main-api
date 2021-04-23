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

package org.make.api.operation

import java.time.{LocalDate, ZonedDateTime}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import cats.data.NonEmptyList
import eu.timepit.refined.W
import eu.timepit.refined.auto._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import eu.timepit.refined.collection.MaxSize
import grizzled.slf4j.Logging
import io.circe.refined._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.core.Validation.{
  requirePresent,
  validate,
  validateColor,
  validateField,
  validateOptionalUserInput,
  validateUserInput
}
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.operation.OperationKind.GreatCause
import org.make.core.operation.OperationOfQuestion.{Status => QuestionStatus}
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.user.Role.RoleAdmin
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import org.make.core.technical.Pagination._

@Api(value = "Moderation Operation of question")
@Path(value = "/moderation/operations-of-questions")
trait ModerationOperationOfQuestionApi extends Directives {

  @ApiOperation(
    value = "get-operations-of-questions-for-operation",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[OperationOfQuestionResponse]])
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", required = false, dataType = "integer"),
      new ApiImplicitParam(name = "_end", paramType = "query", required = false, dataType = "integer"),
      new ApiImplicitParam(name = "_sort", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(
        name = "operationKind",
        paramType = "query",
        required = false,
        dataType = "string",
        allowableValues = "GREAT_CAUSE,PRIVATE_CONSULTATION,BUSINESS_CONSULTATION",
        allowMultiple = true
      ),
      new ApiImplicitParam(name = "openAt", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "endAfter", paramType = "query", required = false, dataType = "string")
    )
  )
  @Path(value = "/")
  def listOperationOfQuestions: Route

  @ApiOperation(
    value = "get-operation-of-question",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"))
  )
  @Path(value = "/{questionId}")
  def getOperationOfQuestion: Route

  @ApiOperation(
    value = "modify-operation-of-question",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        required = true,
        dataType = "org.make.api.operation.ModifyOperationOfQuestionRequest"
      )
    )
  )
  @Path(value = "/{questionId}")
  def modifyOperationOfQuestion: Route

  @ApiOperation(
    value = "delete-operation-of-question",
    httpMethod = "DELETE",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"))
  )
  @Path(value = "/{questionId}")
  def deleteOperationOfQuestionAndQuestion: Route

  @ApiOperation(
    value = "create-operation-of-question",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        required = true,
        dataType = "org.make.api.operation.CreateOperationOfQuestionRequest"
      )
    )
  )
  @Path(value = "/")
  def createOperationOfQuestionAndQuestion: Route

  def routes: Route =
    listOperationOfQuestions ~
      getOperationOfQuestion ~
      modifyOperationOfQuestion ~
      deleteOperationOfQuestionAndQuestion ~
      createOperationOfQuestionAndQuestion
}

trait ModerationOperationOfQuestionApiComponent {
  def moderationOperationOfQuestionApi: ModerationOperationOfQuestionApi
}

trait DefaultModerationOperationOfQuestionApiComponent
    extends ModerationOperationOfQuestionApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies
    with OperationOfQuestionServiceComponent
    with QuestionServiceComponent
    with OperationServiceComponent =>

  val moderationQuestionId: PathMatcher1[QuestionId] = Segment.map(id   => QuestionId(id))
  val moderationOperationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))

  override lazy val moderationOperationOfQuestionApi: DefaultModerationOperationOfQuestionApi =
    new DefaultModerationOperationOfQuestionApi

  class DefaultModerationOperationOfQuestionApi extends ModerationOperationOfQuestionApi {

    override def listOperationOfQuestions: Route = get {
      path("moderation" / "operations-of-questions") {
        makeOperation("ListOperationsOfQuestions") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              parameters(
                "_start".as[Start].?,
                "_end".as[End].?,
                "_sort".?,
                "_order".as[Order].?,
                "questionId".as[Seq[QuestionId]].?,
                "operationId".as[OperationId].?,
                "operationKind".csv[OperationKind],
                "openAt".as[ZonedDateTime].?,
                "endAfter".as[ZonedDateTime].?,
                "slug".as[String].?
              ) {
                (
                  start: Option[Start],
                  end: Option[End],
                  sort: Option[String],
                  order: Option[Order],
                  questionIds,
                  operationId,
                  operationKinds,
                  openAt,
                  endAfter,
                  slug
                ) =>
                  val resolvedQuestions: Option[Seq[QuestionId]] = {
                    if (auth.user.roles.contains(RoleAdmin)) {
                      questionIds
                    } else {
                      questionIds.map { questions =>
                        questions.filter(id => auth.user.availableQuestions.contains(id))
                      }.orElse(Some(auth.user.availableQuestions))
                    }
                  }
                  provideAsync(
                    operationOfQuestionService
                      .find(
                        start.orZero,
                        end,
                        sort,
                        order,
                        SearchOperationsOfQuestions(
                          questionIds = resolvedQuestions,
                          operationIds = operationId.map(opId => Seq(opId)),
                          operationKind = operationKinds,
                          openAt = openAt,
                          endAfter = endAfter,
                          slug = slug
                        )
                      )
                  ) { result: Seq[OperationOfQuestion] =>
                    provideAsync(
                      operationOfQuestionService
                        .count(
                          SearchOperationsOfQuestions(
                            questionIds = resolvedQuestions,
                            operationIds = operationId.map(opId => Seq(opId)),
                            operationKind = operationKinds,
                            openAt = openAt,
                            endAfter = endAfter,
                            slug = slug
                          )
                        )
                    ) { count =>
                      provideAsync(questionService.getQuestions(result.map(_.questionId))) { questions: Seq[Question] =>
                        val questionsAsMap = questions.map(q => q.questionId -> q).toMap
                        complete(
                          (
                            StatusCodes.OK,
                            List(`X-Total-Count`(count.toString)),
                            result
                              .map(
                                operationOfQuestion =>
                                  OperationOfQuestionResponse(
                                    operationOfQuestion,
                                    questionsAsMap(operationOfQuestion.questionId)
                                  )
                              )
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
    }

    override def getOperationOfQuestion: Route = get {
      path("moderation" / "operations-of-questions" / moderationQuestionId) { questionId =>
        makeOperation("GetOperationsOfQuestions") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(questionId)) { operationOfQuestion =>
                  complete(OperationOfQuestionResponse(operationOfQuestion, question))
                }
              }
            }
          }
        }
      }
    }

    override def modifyOperationOfQuestion: Route = put {
      path("moderation" / "operations-of-questions" / moderationQuestionId) { questionId =>
        makeOperation("ModifyOperationsOfQuestions") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[ModifyOperationOfQuestionRequest]) { request =>
                  provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                    val condition =
                      question.countries.toList.intersect(request.countries.toList) == question.countries.toList
                    Validation.validate(
                      validateField(
                        "countries",
                        "invalid_value",
                        condition,
                        s"You can not remove existing countries: ${question.countries.toList.mkString(", ")}"
                      )
                    )
                    provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(questionId)) {
                      operationOfQuestion =>
                        val updatedQuestion = question.copy(
                          countries = request.countries,
                          question = request.question,
                          shortTitle = request.shortTitle.map(_.value)
                        )
                        val updatedSequenceCardsConfiguration =
                          request.sequenceCardsConfiguration.copy(
                            introCard = request.sequenceCardsConfiguration.introCard.copy(
                              title = if (request.sequenceCardsConfiguration.introCard.enabled) {
                                request.sequenceCardsConfiguration.introCard.title
                              } else {
                                None
                              },
                              description = if (request.sequenceCardsConfiguration.introCard.enabled) {
                                request.sequenceCardsConfiguration.introCard.description
                              } else {
                                None
                              }
                            ),
                            pushProposalCard = PushProposalCard(enabled = request.canPropose &&
                              request.sequenceCardsConfiguration.pushProposalCard.enabled
                            ),
                            finalCard = request.sequenceCardsConfiguration.finalCard
                              .copy(sharingEnabled = request.sequenceCardsConfiguration.finalCard.enabled &&
                                request.sequenceCardsConfiguration.finalCard.sharingEnabled
                              )
                          )
                        onSuccess(
                          operationOfQuestionService.updateWithQuestion(
                            operationOfQuestion
                              .copy(
                                startDate = request.startDate,
                                endDate = request.endDate,
                                operationTitle = request.operationTitle,
                                canPropose = request.canPropose,
                                sequenceCardsConfiguration = updatedSequenceCardsConfiguration,
                                aboutUrl = request.aboutUrl.map(_.value),
                                metas = request.metas,
                                theme = request.theme,
                                description = request.description.getOrElse(OperationOfQuestion.defaultDescription),
                                consultationImage = request.consultationImage.map(_.value),
                                consultationImageAlt = request.consultationImageAlt,
                                descriptionImage = request.descriptionImage.map(_.value),
                                descriptionImageAlt = request.descriptionImageAlt,
                                resultsLink = Option
                                  .when(request.displayResults)(request.resultsLink.flatMap(_.resultsLink))
                                  .flatten,
                                actions = request.actions,
                                featured = request.featured,
                                votesTarget = request.votesTarget,
                                timeline = request.timeline.toOOQTimeline
                              ),
                            updatedQuestion
                          )
                        ) { result =>
                          complete(StatusCodes.OK -> OperationOfQuestionResponse(result, updatedQuestion))
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

    override def deleteOperationOfQuestionAndQuestion: Route = delete {
      path("moderation" / "operations-of-questions" / moderationQuestionId) { questionId =>
        makeOperation("DeleteOperationsOfQuestions") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              provideAsync(operationOfQuestionService.delete(questionId)) { _ =>
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }

    override def createOperationOfQuestionAndQuestion: Route = post {
      path("moderation" / "operations-of-questions") {
        makeOperation("CreateOperationsOfQuestions") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[CreateOperationOfQuestionRequest]) { body =>
                  provideAsync(operationService.findOneSimple(body.operationId)) { maybeOperation =>
                    provideAsync(operationOfQuestionService.findByQuestionSlug(body.questionSlug)) { maybeQuestion =>
                      Validation.validate(
                        Validation
                          .requireNotPresent(
                            "slug",
                            maybeQuestion,
                            Some(s"Slug '${body.questionSlug}' already exists")
                          ),
                        Validation
                          .requirePresent(
                            "operationId",
                            maybeOperation,
                            Some(s"Operation '${body.operationId}' does not exist")
                          )
                      )
                      provideAsync(
                        operationOfQuestionService.create(
                          CreateOperationOfQuestion(
                            operationId = body.operationId,
                            startDate = body.startDate,
                            endDate = body.endDate,
                            operationTitle = body.operationTitle,
                            slug = body.questionSlug,
                            countries = body.countries,
                            language = body.language,
                            question = body.question,
                            shortTitle = body.shortTitle.map(_.value),
                            consultationImage = body.consultationImage.map(_.value),
                            consultationImageAlt = body.consultationImageAlt,
                            descriptionImage = body.descriptionImage.map(_.value),
                            descriptionImageAlt = body.descriptionImageAlt,
                            actions = body.actions,
                            featured = maybeOperation.exists(_.operationKind == GreatCause)
                          )
                        )
                      ) { operationOfQuestion =>
                        provideAsyncOrNotFound(questionService.getQuestion(operationOfQuestion.questionId)) {
                          question =>
                            complete(StatusCodes.Created -> OperationOfQuestionResponse(operationOfQuestion, question))
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
    }
  }
}

@ApiModel
final case class ModifyOperationOfQuestionRequest(
  @(ApiModelProperty @field)(dataType = "dateTime")
  startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  endDate: ZonedDateTime,
  operationTitle: String,
  @(ApiModelProperty @field)(dataType = "list[string]")
  countries: NonEmptyList[Country],
  question: String,
  @(ApiModelProperty @field)(dataType = "string")
  shortTitle: Option[String Refined MaxSize[W.`30`.T]],
  canPropose: Boolean,
  sequenceCardsConfiguration: SequenceCardsConfiguration,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/about")
  aboutUrl: Option[String Refined Url],
  metas: Metas,
  theme: QuestionTheme,
  description: Option[String],
  displayResults: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/consultation-image.png")
  consultationImage: Option[String Refined Url],
  @(ApiModelProperty @field)(dataType = "string", example = "consultation image alternative")
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/description-image.png")
  descriptionImage: Option[String Refined Url],
  @(ApiModelProperty @field)(dataType = "string", example = "description image alternative")
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  resultsLink: Option[ResultsLinkRequest],
  actions: Option[String],
  featured: Boolean,
  votesTarget: Int,
  timeline: OperationOfQuestionTimelineContract
) {

  validate(
    Seq(
      validateUserInput("question", question, None),
      validateUserInput("operationTitle", operationTitle, None),
      validateOptionalUserInput("shortTitle", shortTitle.map(_.value), None),
      validateOptionalUserInput("description", description, None),
      validateColor("color", theme.color, None),
      validateColor("fontColor", theme.fontColor, None),
      validateField(
        "consultationImage",
        "not_secure",
        consultationImage.forall(_.value.startsWith("https://")),
        "consultationImage must be a secure https url"
      ),
      validateField(
        "descriptionImage",
        "not_secure",
        descriptionImage.forall(_.value.startsWith("https://")),
        "descriptionImage must be a secure https url"
      )
    ): _*
  )
}

object ModifyOperationOfQuestionRequest extends CirceFormatters {
  implicit val decoder: Decoder[ModifyOperationOfQuestionRequest] = deriveDecoder[ModifyOperationOfQuestionRequest]
  implicit val encoder: Encoder[ModifyOperationOfQuestionRequest] = deriveEncoder[ModifyOperationOfQuestionRequest]
}

@ApiModel
final case class CreateOperationOfQuestionRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId,
  @(ApiModelProperty @field)(dataType = "dateTime")
  startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  endDate: ZonedDateTime,
  operationTitle: String,
  @(ApiModelProperty @field)(dataType = "list[string]")
  countries: NonEmptyList[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  question: String,
  @(ApiModelProperty @field)(dataType = "string")
  shortTitle: Option[String Refined MaxSize[W.`30`.T]],
  questionSlug: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/consultation-image.png")
  consultationImage: Option[String Refined Url],
  @(ApiModelProperty @field)(dataType = "string", example = "consultation image alternative")
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/description-image.png")
  descriptionImage: Option[String Refined Url] = None,
  @(ApiModelProperty @field)(dataType = "string", example = "description image alternative")
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]] = None,
  actions: Option[String]
) {
  validate(
    validateUserInput("operationTitle", operationTitle, None),
    validateUserInput("question", question, None),
    validateOptionalUserInput("shortTitle", shortTitle.map(_.value), None),
    validateUserInput("questionSlug", questionSlug, None),
    validateField(
      "consultationImage",
      "not_secure",
      consultationImage.forall(_.value.startsWith("https://")),
      "consultationImage must be a secure https url"
    ),
    validateField(
      "descriptionImage",
      "not_secure",
      descriptionImage.forall(_.value.startsWith("https://")),
      "descriptionImage must be a secure https url"
    )
  )
}

object CreateOperationOfQuestionRequest extends CirceFormatters {
  implicit val decoder: Decoder[CreateOperationOfQuestionRequest] = deriveDecoder[CreateOperationOfQuestionRequest]
  implicit val encoder: Encoder[CreateOperationOfQuestionRequest] = deriveEncoder[CreateOperationOfQuestionRequest]
}

final case class OperationOfQuestionTimelineContract(
  action: TimelineElementContract,
  result: TimelineElementContract,
  workshop: TimelineElementContract
) {
  def toOOQTimeline: OperationOfQuestionTimeline = OperationOfQuestionTimeline(
    action = action.toTimelineElement,
    result = result.toTimelineElement,
    workshop = workshop.toTimelineElement
  )
}

object OperationOfQuestionTimelineContract extends CirceFormatters {
  implicit val decoder: Decoder[OperationOfQuestionTimelineContract] =
    deriveDecoder[OperationOfQuestionTimelineContract]
  implicit val encoder: Encoder[OperationOfQuestionTimelineContract] =
    deriveEncoder[OperationOfQuestionTimelineContract]

  def apply(timeline: OperationOfQuestionTimeline): OperationOfQuestionTimelineContract =
    OperationOfQuestionTimelineContract(
      action = TimelineElementContract(timeline.action),
      result = TimelineElementContract(timeline.result),
      workshop = TimelineElementContract(timeline.workshop)
    )
}

final case class TimelineElementContract(
  defined: Boolean,
  date: Option[LocalDate],
  dateText: Option[String Refined MaxSize[W.`20`.T]],
  description: Option[String Refined MaxSize[W.`150`.T]]
) {
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def toTimelineElement: Option[TimelineElement] =
    if (!defined) {
      None
    } else {
      validate(
        requirePresent("timeline.date", date),
        requirePresent("timeline.dateText", dateText),
        requirePresent("timeline.description", description)
      )
      for {
        d    <- date
        text <- dateText
        desc <- description
      } yield TimelineElement(date = d, dateText = text, description = desc)
    }
}

object TimelineElementContract extends CirceFormatters {
  implicit val decoder: Decoder[TimelineElementContract] = deriveDecoder[TimelineElementContract]
  implicit val encoder: Encoder[TimelineElementContract] = deriveEncoder[TimelineElementContract]

  def apply(timelineElement: Option[TimelineElement]): TimelineElementContract = TimelineElementContract(
    defined = timelineElement.isDefined,
    date = timelineElement.map(_.date),
    dateText = timelineElement.map(_.dateText),
    description = timelineElement.map(_.description)
  )
}

@ApiModel
final case class OperationOfQuestionResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "d2b2694a-25cf-4eaa-9181-026575d58cf8")
  id: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "49207ae1-0732-42f5-a0d0-af4ff8c4c2de")
  operationId: OperationId,
  @(ApiModelProperty @field)(dataType = "dateTime")
  startDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "dateTime")
  endDate: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "string", example = "fd735649-e63d-4464-9d93-10da54510a12")
  landingSequenceId: SequenceId,
  operationTitle: String,
  slug: String,
  question: String,
  shortTitle: Option[String],
  @(ApiModelProperty @field)(dataType = "list[string]")
  countries: NonEmptyList[Country],
  @(ApiModelProperty @field)(dataType = "string", example = "fr")
  language: Language,
  canPropose: Boolean,
  sequenceCardsConfiguration: SequenceCardsConfiguration,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/about")
  aboutUrl: Option[String],
  metas: Metas,
  theme: QuestionTheme,
  description: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/consultation-image.png")
  consultationImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "consultation image alternative")
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/description-image.png")
  descriptionImage: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "description image alternative")
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  displayResults: Boolean,
  resultsLink: Option[ResultsLinkResponse],
  actions: Option[String],
  featured: Boolean,
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "upcoming,open,finished")
  status: QuestionStatus,
  votesTarget: Int,
  timeline: OperationOfQuestionTimelineContract,
  createdAt: ZonedDateTime
)

object OperationOfQuestionResponse extends CirceFormatters {
  implicit val decoder: Decoder[OperationOfQuestionResponse] = deriveDecoder[OperationOfQuestionResponse]
  implicit val encoder: Encoder[OperationOfQuestionResponse] = deriveEncoder[OperationOfQuestionResponse]

  def apply(operationOfQuestion: OperationOfQuestion, question: Question): OperationOfQuestionResponse = {
    OperationOfQuestionResponse(
      id = operationOfQuestion.questionId,
      operationId = operationOfQuestion.operationId,
      operationTitle = operationOfQuestion.operationTitle,
      startDate = operationOfQuestion.startDate,
      endDate = operationOfQuestion.endDate,
      landingSequenceId = operationOfQuestion.landingSequenceId,
      slug = question.slug,
      question = question.question,
      shortTitle = question.shortTitle,
      countries = question.countries,
      language = question.language,
      canPropose = operationOfQuestion.canPropose,
      sequenceCardsConfiguration = operationOfQuestion.sequenceCardsConfiguration,
      aboutUrl = operationOfQuestion.aboutUrl,
      metas = operationOfQuestion.metas,
      theme = operationOfQuestion.theme,
      description = operationOfQuestion.description,
      consultationImage = operationOfQuestion.consultationImage,
      consultationImageAlt = operationOfQuestion.consultationImageAlt,
      descriptionImage = operationOfQuestion.descriptionImage,
      descriptionImageAlt = operationOfQuestion.descriptionImageAlt,
      displayResults = operationOfQuestion.resultsLink.isDefined,
      resultsLink = operationOfQuestion.resultsLink.map(ResultsLinkResponse.apply),
      actions = operationOfQuestion.actions,
      featured = operationOfQuestion.featured,
      status = operationOfQuestion.status,
      votesTarget = operationOfQuestion.votesTarget,
      timeline = OperationOfQuestionTimelineContract(operationOfQuestion.timeline),
      createdAt = operationOfQuestion.createdAt
    )
  }
}
