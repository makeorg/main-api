package org.make.api.proposition

import org.make.api.technical.SprayJsonFormatters
import org.make.core.proposition.Proposition
import org.make.core.proposition.PropositionEvent.{PropositionProposed, PropositionUpdated, PropositionViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.json._
import stamina.{StaminaAkkaSerializer, V1}

class PropositionSerializers
    extends StaminaAkkaSerializer(
      PropositionSerializers.propositionProposedSerializer,
      PropositionSerializers.propositionViewedSerializer,
      PropositionSerializers.propositionUpdatedSerializer,
      PropositionSerializers.propositionSerializer
    )

object PropositionSerializers extends SprayJsonFormatters {

  implicit val propositionProposedFormatter: RootJsonFormat[PropositionProposed] =
    DefaultJsonProtocol.jsonFormat4(PropositionProposed)

  implicit val propositionViewedFormatter: RootJsonFormat[PropositionViewed] =
    DefaultJsonProtocol.jsonFormat1(PropositionViewed)

  implicit val propositionUpdatedFormatter: RootJsonFormat[PropositionUpdated] =
    DefaultJsonProtocol.jsonFormat3(PropositionUpdated)

  implicit val propositionFormatter: RootJsonFormat[Proposition] =
    DefaultJsonProtocol.jsonFormat5(Proposition)

  val propositionProposedSerializer: JsonPersister[PropositionProposed, V1] =
    persister[PropositionProposed]("proposition-proposed")

  val propositionViewedSerializer: JsonPersister[PropositionViewed, V1] =
    persister[PropositionViewed]("proposition-viewed")

  val propositionUpdatedSerializer: JsonPersister[PropositionUpdated, V1] =
    persister[PropositionUpdated]("proposition-updated")

  val propositionSerializer: JsonPersister[Proposition, V1] =
    persister[Proposition]("proposition")

}
