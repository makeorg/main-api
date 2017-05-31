package org.make.api.proposition

import org.make.api.technical.SprayJsonFormatters
import org.make.core.proposition.Proposition
import org.make.core.proposition.PropositionEvent.{PropositionProposed, PropositionUpdated, PropositionViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object PropositionSerializers extends SprayJsonFormatters {

  implicit private val propositionProposedFormatter: RootJsonFormat[PropositionProposed] =
    DefaultJsonProtocol.jsonFormat4(PropositionProposed)

  implicit private val propositionViewedFormatter: RootJsonFormat[PropositionViewed] =
    DefaultJsonProtocol.jsonFormat1(PropositionViewed)

  implicit private val propositionUpdatedFormatter: RootJsonFormat[PropositionUpdated] =
    DefaultJsonProtocol.jsonFormat3(PropositionUpdated)

  implicit private val propositionFormatter: RootJsonFormat[Proposition] =
    DefaultJsonProtocol.jsonFormat5(Proposition)

  private val propositionProposedSerializer: JsonPersister[PropositionProposed, V1] =
    persister[PropositionProposed]("proposition-proposed")

  private val propositionViewedSerializer: JsonPersister[PropositionViewed, V1] =
    persister[PropositionViewed]("proposition-viewed")

  private val propositionUpdatedSerializer: JsonPersister[PropositionUpdated, V1] =
    persister[PropositionUpdated]("proposition-updated")

  private val propositionSerializer: JsonPersister[Proposition, V1] =
    persister[Proposition]("proposition")

  val serializers: Seq[JsonPersister[_ , _]] = Seq(
    propositionProposedSerializer,
    propositionViewedSerializer,
    propositionUpdatedSerializer,
    propositionSerializer
  )
}
