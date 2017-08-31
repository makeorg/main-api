package org.make.core.proposal.indexed

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}

sealed trait QualificationKey { val shortName: String }

object QualificationKey extends StrictLogging {
  private val qualificationKeys: Map[String, QualificationKey] = Map(
    LikeIt.shortName -> LikeIt,
    Doable.shortName -> Doable,
    PlatitudeAgree.shortName -> PlatitudeAgree,
    NoWay.shortName -> NoWay,
    Impossible.shortName -> Impossible,
    PlatitudeDisagree.shortName -> PlatitudeDisagree,
    DoNotUnderstand.shortName -> DoNotUnderstand,
    NoOpinion.shortName -> NoOpinion,
    DoNotCare.shortName -> DoNotCare
  )

  implicit val qualificationKeyEncoder: Encoder[QualificationKey] =
    (qualificationKey: QualificationKey) => Json.fromString(qualificationKey.shortName)
  implicit val qualificationKeyDecoder: Decoder[QualificationKey] =
    Decoder.decodeString.map(
      qualificationKey =>
        QualificationKey
          .matchQualificationKey(qualificationKey)
          .getOrElse(throw new IllegalArgumentException(s"$qualificationKey is not a QualificationKey"))
    )

  def matchQualificationKey(qualificationKey: String): Option[QualificationKey] = {
    val maybeQualificationKey = qualificationKeys.get(qualificationKey)
    if (maybeQualificationKey.isEmpty) {
      logger.warn(s"$qualificationKey is not a qualification key")
    }
    maybeQualificationKey
  }

  case object LikeIt extends QualificationKey { override val shortName: String = "likeIt" }
  case object Doable extends QualificationKey { override val shortName: String = "doable" }
  case object PlatitudeAgree extends QualificationKey { override val shortName: String = "platitudeAgree" }
  case object NoWay extends QualificationKey { override val shortName: String = "noWay" }
  case object Impossible extends QualificationKey { override val shortName: String = "impossible" }
  case object PlatitudeDisagree extends QualificationKey { override val shortName: String = "platitudeDisagree" }
  case object DoNotUnderstand extends QualificationKey { override val shortName: String = "doNotUnderstand" }
  case object NoOpinion extends QualificationKey { override val shortName: String = "noOpinion" }
  case object DoNotCare extends QualificationKey { override val shortName: String = "doNotCare" }
}

final case class Qualification(key: QualificationKey, count: Int = 0, selected: Boolean = false)
