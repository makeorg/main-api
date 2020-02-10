package org.make.core.idea

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.user.UserId
import spray.json.{JsString, JsValue, JsonFormat}

final case class TopIdeaCommentId(value: String) extends StringValue

object TopIdeaCommentId {
  implicit lazy val topIdeaCommentIdEncoder: Encoder[TopIdeaCommentId] =
    (a: TopIdeaCommentId) => Json.fromString(a.value)
  implicit lazy val topIdeaCommentIdDecoder: Decoder[TopIdeaCommentId] =
    Decoder.decodeString.map(TopIdeaCommentId(_))

  implicit val topIdeaCommentIdFormatter: JsonFormat[TopIdeaCommentId] = new JsonFormat[TopIdeaCommentId] {
    override def read(json: JsValue): TopIdeaCommentId = json match {
      case JsString(value) => TopIdeaCommentId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TopIdeaCommentId): JsValue = {
      JsString(obj.value)
    }
  }
}

final case class TopIdeaComment(topIdeaCommentId: TopIdeaCommentId,
                                topIdeaId: TopIdeaId,
                                personalityId: UserId,
                                comment1: Option[String],
                                comment2: Option[String],
                                comment3: Option[String],
                                vote: CommentVoteKey,
                                qualification: Option[CommentQualificationKey],
                                createdAt: Option[ZonedDateTime] = None,
                                updatedAt: Option[ZonedDateTime] = None)

object TopIdeaComment {
  implicit val encoder: Encoder[TopIdeaComment] = deriveEncoder[TopIdeaComment]
  implicit val decoder: Decoder[TopIdeaComment] = deriveDecoder[TopIdeaComment]
}

sealed trait CommentVoteKey { val shortName: String }

object CommentVoteKey extends StrictLogging {
  val voteKeys: Map[String, CommentVoteKey] =
    Map(Agree.shortName -> Agree, Disagree.shortName -> Disagree, Other.shortName -> Other)

  implicit lazy val voteKeyEncoder: Encoder[CommentVoteKey] =
    (voteKey: CommentVoteKey) => Json.fromString(voteKey.shortName)
  implicit lazy val voteKeyDecoder: Decoder[CommentVoteKey] =
    Decoder.decodeString.emap(
      commentVoteKey =>
        CommentVoteKey
          .matchCommentVoteKey(commentVoteKey)
          .map(Right.apply)
          .getOrElse(Left(s"$commentVoteKey is not a CommentVoteKey"))
    )

  implicit val voteKeyFormatter: JsonFormat[CommentVoteKey] = new JsonFormat[CommentVoteKey] {
    override def read(json: JsValue): CommentVoteKey = json match {
      case JsString(s) =>
        CommentVoteKey.voteKeys.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: CommentVoteKey): JsValue = {
      JsString(obj.shortName)
    }
  }

  def matchCommentVoteKey(commentVoteKey: String): Option[CommentVoteKey] = {
    val maybeCommentVoteKey = voteKeys.get(commentVoteKey)
    if (maybeCommentVoteKey.isEmpty) {
      logger.warn(s"$commentVoteKey is not a voteKey")
    }
    maybeCommentVoteKey
  }

  case object Agree extends CommentVoteKey { override val shortName: String = "agree" }
  case object Disagree extends CommentVoteKey { override val shortName: String = "disagree" }
  case object Other extends CommentVoteKey { override val shortName: String = "other" }
}

sealed trait CommentQualificationKey {
  val shortName: String
  val commentVoteKey: CommentVoteKey
}

object CommentQualificationKey extends StrictLogging {
  val qualificationKeys: Map[String, CommentQualificationKey] = Map(
    Priority.shortName -> Priority,
    Doable.shortName -> Doable,
    NoWay.shortName -> NoWay,
    NonPriority.shortName -> NonPriority,
    Exists.shortName -> Exists,
    ToBePrecised.shortName -> ToBePrecised
  )

  implicit val qualificationKeyEncoder: Encoder[CommentQualificationKey] =
    (qualificationKey: CommentQualificationKey) => Json.fromString(qualificationKey.shortName)
  implicit val qualificationKeyDecoder: Decoder[CommentQualificationKey] =
    Decoder.decodeString.emap(
      commentQualificationKey =>
        CommentQualificationKey
          .matchCommentQualificationKey(commentQualificationKey)
          .map(Right.apply)
          .getOrElse(Left(s"$commentQualificationKey is not a CommentQualificationKey"))
    )

  implicit val qualificationKeyFormatter: JsonFormat[CommentQualificationKey] =
    new JsonFormat[CommentQualificationKey] {
      override def read(json: JsValue): CommentQualificationKey = json match {
        case JsString(s) =>
          CommentQualificationKey.qualificationKeys
            .getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
        case other => throw new IllegalArgumentException(s"Unable to convert $other")
      }

      override def write(obj: CommentQualificationKey): JsValue = {
        JsString(obj.shortName)
      }
    }

  def matchCommentQualificationKey(commentQualificationKey: String): Option[CommentQualificationKey] = {
    val maybeCommentQualificationKey = qualificationKeys.get(commentQualificationKey)
    if (maybeCommentQualificationKey.isEmpty) {
      logger.warn(s"$commentQualificationKey is not a qualification key")
    }
    maybeCommentQualificationKey
  }

  case object Priority extends CommentQualificationKey {
    override val shortName: String = "priority"
    override val commentVoteKey: CommentVoteKey = CommentVoteKey.Agree
  }
  case object Doable extends CommentQualificationKey {
    override val shortName: String = "doable"
    override val commentVoteKey: CommentVoteKey = CommentVoteKey.Agree
  }
  case object NoWay extends CommentQualificationKey {
    override val shortName: String = "noWay"
    override val commentVoteKey: CommentVoteKey = CommentVoteKey.Disagree
  }
  case object NonPriority extends CommentQualificationKey {
    override val shortName: String = "nonPriority"
    override val commentVoteKey: CommentVoteKey = CommentVoteKey.Disagree
  }
  case object Exists extends CommentQualificationKey {
    override val shortName: String = "exists"
    override val commentVoteKey: CommentVoteKey = CommentVoteKey.Other
  }
  case object ToBePrecised extends CommentQualificationKey {
    override val shortName: String = "toBePrecised"
    override val commentVoteKey: CommentVoteKey = CommentVoteKey.Other
  }
}
