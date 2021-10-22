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

package org.make.api.technical

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import grizzled.slf4j.Logging
import org.make.api.proposal.ProposalSerializers
import org.make.api.sessionhistory.SessionHistorySerializers
import org.make.api.technical.MakeEventSerializer.allSerializers
import org.make.api.technical.job.JobSerializers
import org.make.api.technical.security.{SecurityConfigurationExtension, SecurityHelper}
import org.make.api.userhistory.UserHistorySerializers
import spray.json.{JsObject, JsString, JsValue}
import stamina.{Persister, StaminaAkkaSerializer}

class MakeEventSerializer(system: ActorSystem[_], serializerName: String)
    extends StaminaAkkaSerializer(allSerializers(system).toList)
    with Logging {

  def this(system: ExtendedActorSystem) = this(system.toTyped, "make-serializer")
  def this(system: ExtendedActorSystem, serializerName: String) = this(system.toTyped, serializerName)

  logger.debug(s"Creating make event serializer with name $serializerName")
}

object MakeEventSerializer {
  def allSerializers(system: ActorSystem[_]): Seq[Persister[_, _]] = {
    val conf = SecurityConfigurationExtension(system)
    ProposalSerializers(conf).serializers ++
      UserHistorySerializers(conf).serializers ++
      SessionHistorySerializers(conf).serializers ++
      JobSerializers.serializers
  }

  def setIpAddressAndHash(salt: String)(context: JsObject): JsObject = {
    val fields: Map[String, JsValue] = context.fields
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    val maybeIp = fields.get("ipAddress").map(_.asInstanceOf[JsString].value)
    JsObject(
      fields ++
        maybeIp.map(ip => "ipAddress" -> JsString(IpAndHash.obfuscateIp(ip))) ++
        maybeIp.map(ip => "ipAddressHash" -> JsString(SecurityHelper.generateHash(ip, salt)))
    )
  }
}
