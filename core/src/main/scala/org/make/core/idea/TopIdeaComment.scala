/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.core.idea

import java.time.ZonedDateTime

import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.make.core.{SprayJsonFormatters, StringValue}
import org.make.core.user.UserId
import spray.json.JsonFormat

final case class TopIdeaCommentId(value: String) extends StringValue

object TopIdeaCommentId {
  implicit lazy val topIdeaCommentIdEncoder: Encoder[TopIdeaCommentId] =
    (a: TopIdeaCommentId) => Json.fromString(a.value)
  implicit lazy val topIdeaCommentIdDecoder: Decoder[TopIdeaCommentId] =
    Decoder.decodeString.map(TopIdeaCommentId(_))

  implicit val topIdeaCommentIdFormatter: JsonFormat[TopIdeaCommentId] =
    SprayJsonFormatters.forStringValue(TopIdeaCommentId.apply)
}

final case class TopIdeaComment(
  topIdeaCommentId: TopIdeaCommentId,
  topIdeaId: TopIdeaId,
  personalityId: UserId,
  comment1: Option[String],
  comment2: Option[String],
  comment3: Option[String],
  vote: CommentVoteKey,
  qualification: Option[CommentQualificationKey],
  createdAt: Option[ZonedDateTime] = None,
  updatedAt: Option[ZonedDateTime] = None
)

object TopIdeaComment {
  implicit val encoder: Encoder[TopIdeaComment] = deriveEncoder[TopIdeaComment]
  implicit val decoder: Decoder[TopIdeaComment] = deriveDecoder[TopIdeaComment]
}

sealed abstract class CommentVoteKey(val value: String) extends StringEnumEntry

object CommentVoteKey extends StringEnum[CommentVoteKey] with StringCirceEnum[CommentVoteKey] {

  case object Agree extends CommentVoteKey("agree")
  case object Disagree extends CommentVoteKey("disagree")
  case object Other extends CommentVoteKey("other")

  override def values: IndexedSeq[CommentVoteKey] = findValues

}

sealed abstract class CommentQualificationKey(val value: String, val commentVoteKey: CommentVoteKey)
    extends StringEnumEntry

object CommentQualificationKey
    extends StringEnum[CommentQualificationKey]
    with StringCirceEnum[CommentQualificationKey] {

  case object Priority extends CommentQualificationKey("priority", CommentVoteKey.Agree)
  case object Doable extends CommentQualificationKey("doable", CommentVoteKey.Agree)
  case object NoWay extends CommentQualificationKey("noWay", CommentVoteKey.Disagree)
  case object NonPriority extends CommentQualificationKey("nonPriority", CommentVoteKey.Disagree)
  case object Exists extends CommentQualificationKey("exists", CommentVoteKey.Other)
  case object ToBePrecised extends CommentQualificationKey("toBePrecised", CommentVoteKey.Other)

  override def values: IndexedSeq[CommentQualificationKey] = findValues

}
