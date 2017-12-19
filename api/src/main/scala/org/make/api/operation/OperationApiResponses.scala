package org.make.api.operation

import java.time.ZonedDateTime

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.CirceFormatters
import org.make.core.operation._
import org.make.core.sequence.SequenceId

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
