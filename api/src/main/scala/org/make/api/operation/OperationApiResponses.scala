package org.make.api.operation

import java.time.ZonedDateTime

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import org.make.api.user.UserResponse
import org.make.core.CirceFormatters
import org.make.core.operation._
import org.make.core.sequence.SequenceId
import org.make.core.user.User

final case class OperationResponse(operationId: OperationId,
                                   status: OperationStatus,
                                   slug: String,
                                   translations: Seq[OperationTranslation] = Seq.empty,
                                   defaultLanguage: String,
                                   sequenceLandingId: SequenceId,
                                   createdAt: Option[ZonedDateTime],
                                   updatedAt: Option[ZonedDateTime],
                                   countriesConfiguration: Seq[OperationCountryConfiguration])

object OperationResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[OperationResponse] = deriveEncoder[OperationResponse]
  implicit val decoder: Decoder[OperationResponse] = deriveDecoder[OperationResponse]

  def apply(operation: Operation): OperationResponse = {
    OperationResponse(
      operationId = operation.operationId,
      status = operation.status,
      slug = operation.slug,
      translations = operation.translations,
      defaultLanguage = operation.defaultLanguage,
      sequenceLandingId = operation.sequenceLandingId,
      createdAt = operation.createdAt,
      updatedAt = operation.updatedAt,
      countriesConfiguration = operation.countriesConfiguration
    )
  }
}

final case class ModerationOperationResponse(operationId: OperationId,
                                             status: OperationStatus,
                                             slug: String,
                                             translations: Seq[OperationTranslation] = Seq.empty,
                                             defaultLanguage: String,
                                             sequenceLandingId: SequenceId,
                                             createdAt: Option[ZonedDateTime],
                                             updatedAt: Option[ZonedDateTime],
                                             events: Seq[OperationActionResponse],
                                             countriesConfiguration: Seq[OperationCountryConfiguration])

object ModerationOperationResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ModerationOperationResponse] = deriveEncoder[ModerationOperationResponse]
  implicit val decoder: Decoder[ModerationOperationResponse] = deriveDecoder[ModerationOperationResponse]

  def apply(operation: Operation, operationActionUsers: Seq[User]): ModerationOperationResponse = {
    val events: Seq[OperationActionResponse] = operation.events.map { action =>
      OperationActionResponse(
        date = action.date,
        user = operationActionUsers
          .filter(_.userId == action.makeUserId)
          .map(userAction => UserResponse.apply(userAction)) match {
          case value if value.isEmpty => None
          case other                  => Some(other.head)
        },
        actionType = action.actionType,
        arguments = action.arguments
      )
    }

    ModerationOperationResponse(
      operationId = operation.operationId,
      status = operation.status,
      slug = operation.slug,
      translations = operation.translations,
      defaultLanguage = operation.defaultLanguage,
      sequenceLandingId = operation.sequenceLandingId,
      createdAt = operation.createdAt,
      updatedAt = operation.updatedAt,
      countriesConfiguration = operation.countriesConfiguration,
      events = events
    )
  }
}

final case class ModerationOperationListResponse(total: Int, results: Seq[ModerationOperationResponse])

object ModerationOperationListResponse {
  implicit val encoder: ObjectEncoder[ModerationOperationListResponse] = deriveEncoder[ModerationOperationListResponse]
  implicit val decoder: Decoder[ModerationOperationListResponse] = deriveDecoder[ModerationOperationListResponse]
}

final case class OperationActionResponse(date: ZonedDateTime,
                                         user: Option[UserResponse],
                                         actionType: String,
                                         arguments: Map[String, String])

object OperationActionResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[OperationActionResponse] = deriveEncoder[OperationActionResponse]
  implicit val decoder: Decoder[OperationActionResponse] = deriveDecoder[OperationActionResponse]
}
