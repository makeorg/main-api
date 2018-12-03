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

package org.make.api.technical.crm

import java.time.ZonedDateTime

import org.make.core.SprayJsonFormatters._
import org.make.core.user.UserId
import org.make.core.{EventWrapper, MakeSerializable}
import shapeless.{:+:, CNil, Coproduct, Poly1}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed trait CrmContactEvent {
  def id: UserId
  def eventDate: ZonedDateTime
}

sealed trait PublishedCrmContactEvent extends CrmContactEvent {
  def version(): Int
}

object PublishedCrmContactEvent {

  val defaultDate: ZonedDateTime = ZonedDateTime.parse("2018-12-03T17:41:05Z")

  type AnyCrmContactEvent =
    CrmContactNew :+:
      CrmContactHardBounce :+:
      CrmContactUnsubscribe :+:
      CrmContactSubscribe :+:
      CrmContactRemoveFromLists :+:
      CrmContactListSync :+:
      CrmContactUpdateProperties :+:
      CNil

  final case class CrmContactEventWrapper(version: Int,
                                          id: String,
                                          date: ZonedDateTime,
                                          eventType: String,
                                          event: AnyCrmContactEvent)
      extends EventWrapper

  object CrmContactEventWrapper {
    def wrapEvent(event: PublishedCrmContactEvent): AnyCrmContactEvent = event match {
      case e: CrmContactNew              => Coproduct[AnyCrmContactEvent](e)
      case e: CrmContactHardBounce       => Coproduct[AnyCrmContactEvent](e)
      case e: CrmContactUnsubscribe      => Coproduct[AnyCrmContactEvent](e)
      case e: CrmContactSubscribe        => Coproduct[AnyCrmContactEvent](e)
      case e: CrmContactRemoveFromLists  => Coproduct[AnyCrmContactEvent](e)
      case e: CrmContactListSync         => Coproduct[AnyCrmContactEvent](e)
      case e: CrmContactUpdateProperties => Coproduct[AnyCrmContactEvent](e)
    }
  }

  object ToCrmContactEvent extends Poly1 {
    implicit val atCrmContactNew: Case.Aux[CrmContactNew, CrmContactNew] = at(identity)
    implicit val atCrmContactHardBounce: Case.Aux[CrmContactHardBounce, CrmContactHardBounce] = at(identity)
    implicit val atCrmContactUnsubscribe: Case.Aux[CrmContactUnsubscribe, CrmContactUnsubscribe] = at(identity)
    implicit val atCrmContactSubscribe: Case.Aux[CrmContactSubscribe, CrmContactSubscribe] = at(identity)
    implicit val atCrmContactRemoveFromLists: Case.Aux[CrmContactRemoveFromLists, CrmContactRemoveFromLists] = at(
      identity
    )
    implicit val atCrmContactListSync: Case.Aux[CrmContactListSync, CrmContactListSync] = at(identity)
    implicit val atCrmContactUpdateProperties: Case.Aux[CrmContactUpdateProperties, CrmContactUpdateProperties] = at(
      identity
    )
  }

  final case class CrmContactNew(id: UserId, eventDate: ZonedDateTime = defaultDate) extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactNew {
    implicit val formatter: RootJsonFormat[CrmContactNew] =
      DefaultJsonProtocol.jsonFormat2(CrmContactNew.apply)
  }

  final case class CrmContactUpdateProperties(id: UserId, eventDate: ZonedDateTime = defaultDate)
      extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactUpdateProperties {
    implicit val formatter: RootJsonFormat[CrmContactNew] =
      DefaultJsonProtocol.jsonFormat2(CrmContactNew.apply)
  }

  final case class CrmContactHardBounce(id: UserId, eventDate: ZonedDateTime = defaultDate)
      extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactHardBounce {
    implicit val formatter: RootJsonFormat[CrmContactHardBounce] =
      DefaultJsonProtocol.jsonFormat2(CrmContactHardBounce.apply)
  }

  final case class CrmContactUnsubscribe(id: UserId, eventDate: ZonedDateTime = defaultDate)
      extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactUnsubscribe {
    implicit val formatter: RootJsonFormat[CrmContactUnsubscribe] =
      DefaultJsonProtocol.jsonFormat2(CrmContactUnsubscribe.apply)
  }

  final case class CrmContactSubscribe(id: UserId, eventDate: ZonedDateTime = defaultDate)
      extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactSubscribe {
    implicit val formatter: RootJsonFormat[CrmContactSubscribe] =
      DefaultJsonProtocol.jsonFormat2(CrmContactSubscribe.apply)
  }

  final case class CrmContactRemoveFromLists(id: UserId, eventDate: ZonedDateTime = defaultDate)
      extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactRemoveFromLists {
    implicit val formatter: RootJsonFormat[CrmContactRemoveFromLists] =
      DefaultJsonProtocol.jsonFormat2(CrmContactRemoveFromLists.apply)
  }

  final case class CrmContactListSync(id: UserId, eventDate: ZonedDateTime = defaultDate)
      extends PublishedCrmContactEvent {
    override def version(): Int = MakeSerializable.V1
  }
  object CrmContactListSync {
    implicit val formatter: RootJsonFormat[CrmContactListSync] =
      DefaultJsonProtocol.jsonFormat2(CrmContactListSync.apply)
  }
}
