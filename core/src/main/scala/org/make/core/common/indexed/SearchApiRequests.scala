package org.make.core.common.indexed

import io.circe.{Decoder, Encoder, Json}
import org.elasticsearch.search.sort.SortOrder
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import org.make.core.SprayJsonFormatters._

sealed trait Order { val shortName: String }

case object OrderAsc extends Order { override val shortName: String = "ASC" }
case object OrderDesc extends Order { override val shortName: String = "DESC" }

object Order {
  implicit lazy val orderEncoder: Encoder[Order] = (order: Order) => Json.fromString(order.shortName)
  implicit lazy val orderDecoder: Decoder[Order] = Decoder.decodeString.map(
    order => matchOrder(order).getOrElse(throw new IllegalArgumentException(s"$order is not a Order"))
  )

  val orders: Map[String, Order] = Map(OrderAsc.shortName -> OrderAsc, OrderDesc.shortName -> OrderDesc)

  def matchOrder(order: String): Option[Order] = {
    val maybeOrder = orders.get(order.toUpperCase)
    maybeOrder
  }
}

final case class SortRequest(field: Option[String], direction: Option[Order]) {
  def toSort: Sort = {
    val maybeOrderDirection = direction match {
      case Some(OrderAsc)  => Some(SortOrder.ASC)
      case Some(OrderDesc) => Some(SortOrder.DESC)
      case None            => None
    }

    Sort(field, maybeOrderDirection)
  }
}

case class Sort(field: Option[String], mode: Option[SortOrder])
object Sort {
  implicit val sortFormatted: RootJsonFormat[Sort] =
    DefaultJsonProtocol.jsonFormat2(Sort.apply)
}
