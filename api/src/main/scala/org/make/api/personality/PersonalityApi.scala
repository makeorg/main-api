package org.make.api.personality

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.{TopIdeaResponse, TopIdeaServiceComponent}
import org.make.api.idea.topIdeaComments.TopIdeaCommentServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.core._
import org.make.core.idea.{TopIdeaComment, TopIdeaCommentId, TopIdeaId}
import org.make.core.profile.Profile
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.user.{User, UserId}
import io.circe.refined._
import org.make.api.operation.{OperationOfQuestionResponse, OperationOfQuestionServiceComponent}
import org.make.api.question.QuestionServiceComponent
import org.make.core.question.QuestionId

import scala.annotation.meta.field

@Api(value = "Personalities")
@Path(value = "/personalities")
trait PersonalityApi extends Directives {

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{userId}")
  def getPersonality: Route

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityProfileResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{userId}/profile")
  def getPersonalityProfile: Route

  @ApiOperation(
    value = "update-personality-profile",
    httpMethod = "PUT",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[PersonalityProfileResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.PersonalityProfileRequest"
      )
    )
  )
  @Path(value = "/{userId}/profile")
  def modifyPersonalityProfile: Route

  @Path("/{userId}/comments")
  @ApiOperation(value = "create-top-idea-comments-for-personality", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.personality.CreateTopIdeaCommentRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[TopIdeaCommentResponse]))
  )
  def createComment: Route

  @ApiOperation(value = "get-personality-opinions", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[PersonalityOpinionResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "cefebb24-4f17-49aa-890b-bc90ed7d6bac"
      ),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string")
    )
  )
  @Path(value = "/{userId}/opinions")
  def getPersonalityOpinions: Route

  def routes: Route =
    getPersonality ~ getPersonalityProfile ~ modifyPersonalityProfile ~ createComment ~ getPersonalityOpinions

}

trait PersonalityApiComponent {
  def personalityApi: PersonalityApi
}

trait DefaultPersonalityApiComponent
    extends PersonalityApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: UserServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with TopIdeaServiceComponent
    with TopIdeaCommentServiceComponent
    with QuestionPersonalityServiceComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent =>

  override lazy val personalityApi: PersonalityApi = new DefaultPersonalityApi

  class DefaultPersonalityApi extends PersonalityApi {

    val userId: PathMatcher1[UserId] = Segment.map(id => UserId(id))

    override def getPersonality: Route =
      get {
        path("personalities" / userId) { userId =>
          makeOperation("GetPersonality") { _ =>
            provideAsyncOrNotFound(userService.getPersonality(userId)) { user =>
              complete(UserResponse(user))
            }
          }
        }
      }

    override def getPersonalityProfile: Route = {
      get {
        path("personalities" / userId / "profile") { userId =>
          makeOperation("GetPersonalityProfile") { _ =>
            provideAsyncOrNotFound(userService.getPersonality(userId)) { user =>
              complete(PersonalityProfileResponse.fromUser(user))
            }
          }
        }
      }
    }

    override def modifyPersonalityProfile: Route = {
      put {
        path("personalities" / userId / "profile") { personalityId =>
          makeOperation("UpdatePersonalityProfile") { requestContext =>
            decodeRequest {
              entity(as[PersonalityProfileRequest]) { request =>
                makeOAuth2 { currentUser =>
                  authorize(currentUser.user.userId == personalityId) {
                    provideAsyncOrNotFound(userService.getPersonality(personalityId)) { personality =>
                      val modifiedProfile = personality.profile
                        .orElse(Profile.parseProfile())
                        .map(
                          _.copy(
                            avatarUrl = request.avatarUrl.map(_.value),
                            description = request.description,
                            optInNewsletter = request.optInNewsletter,
                            website = request.website.map(_.value),
                            politicalParty = Some(request.politicalParty)
                          )
                        )

                      val modifiedPersonality = personality.copy(
                        firstName = Some(request.firstName),
                        lastName = Some(request.lastName),
                        profile = modifiedProfile
                      )

                      provideAsync(userService.update(modifiedPersonality, requestContext)) { result =>
                        complete(PersonalityProfileResponse.fromUser(result))
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

    override def createComment: Route = post {
      path("personalities" / userId / "comments") { userId =>
        makeOperation("CreateTopIdeaComment") { _ =>
          makeOAuth2 { userAuth =>
            authorize(userId == userAuth.user.userId) {
              provideAsyncOrNotFound(userService.getPersonality(userId)) { _ =>
                decodeRequest {
                  entity(as[CreateTopIdeaCommentRequest]) { request =>
                    provideAsync(topIdeaService.getById(request.topIdeaId)) { maybeTopIdea =>
                      Validation.validate(
                        Validation.validateField(
                          "topIdeaId",
                          "invalid_content",
                          maybeTopIdea.isDefined,
                          s"Top idea ${request.topIdeaId} does not exists."
                        )
                      )
                      provideAsync(
                        topIdeaCommentService.create(
                          topIdeaId = request.topIdeaId,
                          personalityId = userId,
                          comment1 = request.comment1,
                          comment2 = request.comment2,
                          comment3 = request.comment3,
                          vote = request.vote,
                          qualification = request.qualification,
                        )
                      ) { comment =>
                        complete(StatusCodes.Created -> TopIdeaCommentResponse(comment))
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

    override def getPersonalityOpinions: Route = get {
      path("personalities" / userId / "opinions") { userId =>
        makeOperation("GetPersonalityOpinions") { _ =>
          parameters(Symbol("questionId").as[QuestionId].?) { maybeQuestionId: Option[QuestionId] =>
            provideAsync(
              questionPersonalityService
                .find(0, None, None, None, userId = Some(userId), questionId = maybeQuestionId, None)
            ) {
              case empty if empty.isEmpty                  => complete(StatusCodes.NotFound)
              case personalities if personalities.size > 1 => complete(StatusCodes.MultipleChoices)
              case Seq(personality) =>
                val questionId = personality.questionId
                provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                  provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(questionId)) { opOfQuestion =>
                    provideAsync(topIdeaService.search(0, None, None, None, None, Some(questionId), None)) { topIdeas =>
                      provideAsync(
                        topIdeaCommentService
                          .search(0, None, Some(topIdeas.map(_.topIdeaId)), Some(Seq(userId)))
                      ) { topIdeaComments =>
                        val response = topIdeas.map { topIdea =>
                          PersonalityOpinionResponse(
                            OperationOfQuestionResponse(opOfQuestion, question),
                            TopIdeaResponse(topIdea),
                            topIdeaComments.find(_.topIdeaId == topIdea.topIdeaId).map(TopIdeaCommentResponse.apply)
                          )
                        }
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
}

final case class PersonalityProfileResponse(firstName: Option[String],
                                            lastName: Option[String],
                                            avatarUrl: Option[String],
                                            description: Option[String],
                                            optInNewsletter: Option[Boolean],
                                            website: Option[String],
                                            politicalParty: Option[String])

object PersonalityProfileResponse {
  implicit val encoder: Encoder[PersonalityProfileResponse] = deriveEncoder[PersonalityProfileResponse]
  implicit val decoder: Decoder[PersonalityProfileResponse] = deriveDecoder[PersonalityProfileResponse]

  def fromUser(user: User): PersonalityProfileResponse = {
    PersonalityProfileResponse(
      firstName = user.firstName,
      lastName = user.lastName,
      avatarUrl = user.profile.flatMap(_.avatarUrl),
      description = user.profile.flatMap(_.description),
      website = user.profile.flatMap(_.website),
      politicalParty = user.profile.flatMap(_.politicalParty),
      optInNewsletter = user.profile.map(_.optInNewsletter)
    )

  }
}

final case class PersonalityProfileRequest(
  firstName: String,
  lastName: String,
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/logo.jpg")
  avatarUrl: Option[String Refined Url],
  description: Option[String],
  optInNewsletter: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "https://make.org")
  website: Option[String Refined Url],
  politicalParty: String
)

object PersonalityProfileRequest {
  implicit val encoder: Encoder[PersonalityProfileRequest] = deriveEncoder[PersonalityProfileRequest]
  implicit val decoder: Decoder[PersonalityProfileRequest] = deriveDecoder[PersonalityProfileRequest]
}

@ApiModel
final case class CreateTopIdeaCommentRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "4b95a17e-145d-496b-8859-6688b3592711")
  topIdeaId: TopIdeaId,
  comment1: Option[String],
  comment2: Option[String],
  comment3: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  vote: Option[VoteKey],
  @(ApiModelProperty @field)(dataType = "string", example = "likeIt")
  qualification: Option[QualificationKey]
)

object CreateTopIdeaCommentRequest {
  implicit val decoder: Decoder[CreateTopIdeaCommentRequest] = deriveDecoder[CreateTopIdeaCommentRequest]
  implicit val encoder: Encoder[CreateTopIdeaCommentRequest] = deriveEncoder[CreateTopIdeaCommentRequest]
}

@ApiModel
final case class TopIdeaCommentResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "828cada5-52e1-4a27-9d45-756766c485d2")
  id: TopIdeaCommentId,
  @(ApiModelProperty @field)(dataType = "string", example = "886251c3-e302-49eb-add5-84cabf46878a")
  topIdeaId: TopIdeaId,
  @(ApiModelProperty @field)(dataType = "string", example = "6002582e-60b9-409a-8aec-6eaf0863101a")
  personalityId: UserId,
  comment1: Option[String],
  comment2: Option[String],
  comment3: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "agree")
  vote: Option[VoteKey],
  @(ApiModelProperty @field)(dataType = "string", example = "likeIt")
  qualification: Option[QualificationKey]
)

object TopIdeaCommentResponse {
  implicit val decoder: Decoder[TopIdeaCommentResponse] = deriveDecoder[TopIdeaCommentResponse]
  implicit val encoder: Encoder[TopIdeaCommentResponse] = deriveEncoder[TopIdeaCommentResponse]

  def apply(comment: TopIdeaComment): TopIdeaCommentResponse =
    TopIdeaCommentResponse(
      id = comment.topIdeaCommentId,
      topIdeaId = comment.topIdeaId,
      personalityId = comment.personalityId,
      comment1 = comment.comment1,
      comment2 = comment.comment2,
      comment3 = comment.comment3,
      vote = comment.vote,
      qualification = comment.qualification
    )
}

@ApiModel
final case class PersonalityOpinionResponse(question: OperationOfQuestionResponse,
                                            topIdea: TopIdeaResponse,
                                            comment: Option[TopIdeaCommentResponse])

object PersonalityOpinionResponse {
  implicit val decoder: Decoder[PersonalityOpinionResponse] = deriveDecoder[PersonalityOpinionResponse]
  implicit val encoder: Encoder[PersonalityOpinionResponse] = deriveEncoder[PersonalityOpinionResponse]
}
