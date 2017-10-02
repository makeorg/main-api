package org.make.core.proposal.indexed

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

sealed trait QualificationKey { val shortName: String }

object QualificationKey extends StrictLogging {
  val qualificationKeys: Map[String, QualificationKey] = Map(
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

  implicit val qualificationKeyFormatter: JsonFormat[QualificationKey] = new JsonFormat[QualificationKey] {
    override def read(json: JsValue): QualificationKey = json match {
      case JsString(s) =>
        QualificationKey.qualificationKeys.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: QualificationKey): JsValue = {
      JsString(obj.shortName)
    }
  }

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

final case class Qualification(key: QualificationKey,
                               count: Int = 0,
                               userIds: Seq[UserId] = Seq.empty,
                               sessionIds: Seq[String] = Seq.empty)

object Qualification {
  implicit val qualificationFormatter: RootJsonFormat[Qualification] =
    DefaultJsonProtocol.jsonFormat4(Qualification.apply)

}

final case class IndexedQualification(key: QualificationKey, count: Int = 0)

object IndexedQualification {
  def apply(qualification: Qualification): IndexedQualification =
    IndexedQualification(key = qualification.key, count = qualification.count)
}
