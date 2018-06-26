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
                                   @Deprecated sequenceLandingId: SequenceId,
                                   createdAt: Option[ZonedDateTime],
                                   updatedAt: Option[ZonedDateTime],
                                   countriesConfiguration: Seq[OperationCountryConfiguration])

object OperationResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[OperationResponse] = deriveEncoder[OperationResponse]
  implicit val decoder: Decoder[OperationResponse] = deriveDecoder[OperationResponse]

  def apply(operation: Operation, countryCode: Option[String]): OperationResponse = {
    OperationResponse(
      operationId = operation.operationId,
      status = operation.status,
      slug = operation.slug,
      translations = operation.translations,
      defaultLanguage = operation.defaultLanguage,
      sequenceLandingId = operation.countriesConfiguration
        .find(conf => countryCode.contains(conf.countryCode))
        .getOrElse(operation.countriesConfiguration.head)
        .landingSequenceId,
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
                                             @Deprecated sequenceLandingId: SequenceId,
                                             createdAt: Option[ZonedDateTime],
                                             updatedAt: Option[ZonedDateTime],
                                             events: Seq[OperationActionResponse],
                                             countriesConfiguration: Seq[OperationCountryConfiguration])

object ModerationOperationResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[ModerationOperationResponse] = deriveEncoder[ModerationOperationResponse]
  implicit val decoder: Decoder[ModerationOperationResponse] = deriveDecoder[ModerationOperationResponse]

  def apply(operation: Operation,
            operationActionUsers: Seq[User],
            countryCode: Option[String]): ModerationOperationResponse = {
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
      sequenceLandingId = operation.countriesConfiguration
        .find(conf => countryCode.contains(conf.countryCode))
        .getOrElse(operation.countriesConfiguration.head)
        .landingSequenceId,
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
