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

package org.make.core.common.indexed

import com.sksamuel.elastic4s.searches.sort.SortOrder
import enumeratum.values.{StringEnum, StringEnumEntry}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.make.core.SprayJsonFormatters._
import org.make.core.technical.enumeratum.EnumKeys.StringEnumKeys
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed abstract class Order(val value: String) extends StringEnumEntry

object Order extends StringEnum[Order] with StringEnumKeys[Order] {

  def parse(order: String): Option[Order] = withValueOpt(order.toUpperCase)

  case object OrderAsc extends Order("ASC")
  case object OrderDesc extends Order("DESC")

  override def values: IndexedSeq[Order] = findValues

  implicit val orderDecoder: Decoder[Order] =
    Decoder[String].emap(order => parse(order).toRight(s"$order is not a Order"))
  implicit val orderEncoder: Encoder[Order] = Encoder[String].contramap(_.value)

}

final case class SortRequest(field: Option[String], direction: Option[Order]) {
  def toSort: Sort = {
    val maybeOrderDirection = direction match {
      case Some(Order.OrderAsc)  => Some(SortOrder.ASC)
      case Some(Order.OrderDesc) => Some(SortOrder.DESC)
      case None                  => None
    }

    Sort(field, maybeOrderDirection)
  }
}

object SortRequest {
  implicit val decoder: Decoder[SortRequest] = deriveDecoder[SortRequest]
}

case class Sort(field: Option[String], mode: Option[SortOrder])

object Sort {
  implicit val decoder: Decoder[Sort] = deriveDecoder[Sort]
  implicit val sortOrderDecoder: Decoder[SortOrder] = Decoder.decodeString.emap {
    case asc if asc.toLowerCase == "asc"    => Right(SortOrder.ASC)
    case desc if desc.toLowerCase == "desc" => Right(SortOrder.DESC)
    case other                              => Left(s"Unrecognized sort option $other, expected 'asc' or 'desc'")
  }

  implicit val sortFormatted: RootJsonFormat[Sort] =
    DefaultJsonProtocol.jsonFormat2(Sort.apply)
}
