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

package org.make.api
import akka.actor.typed.ActorSystem
import akka.serialization.{Serialization, SerializationExtension}

import java.nio.ByteOrder
import stamina.ByteString

object StaminaTestUtils {

//  copied from org.apache.logging.log4j.core.config.plugins.convert.HexConverter
  @SuppressWarnings(Array("org.wartremover.warts.While"))
  private def parseHexBinary(s: String): Array[Byte] = {
    val len = s.length
    val data = new Array[Byte](len / 2)
    var i = 0
    while ({ i < len }) {
      data(i / 2) = ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16)).toByte
      i += 2
    }
    data
  }

  private val initialCharactersToSkip: Int = 2

  @SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
  private def makeEventSerializer(implicit system: ActorSystem[_]) =
    SerializationExtension(system)
      .serializerOf(new Serialization.Settings(system.settings.config).Serializers("make-serializer"))
      .get

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def deserializeEventFromJson[A](eventKey: String, eventAsJsonString: String, version: Int = 1)(
    implicit system: ActorSystem[_]
  ): A = {
    implicit val byteOrder: ByteOrder = java.nio.ByteOrder.LITTLE_ENDIAN
    val bytes: Array[Byte] = ByteString.newBuilder
      .putInt(eventKey.length)
      .putBytes(eventKey.getBytes("UTF-8"))
      .putInt(version)
      .append(ByteString(eventAsJsonString))
      .result()
      .toArray

    makeEventSerializer.fromBinary(bytes).asInstanceOf[A]
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def deserializeEventFromHexa[A](serialized: String)(implicit system: ActorSystem[_]): A = {
    val bytes: Array[Byte] = parseHexBinary(serialized.substring(initialCharactersToSkip))

    makeEventSerializer.fromBinary(bytes).asInstanceOf[A]
  }

  def getVersionFromHexa(serialized: String): Int = {
    val bytes: Array[Byte] = parseHexBinary(serialized.substring(initialCharactersToSkip))
    val start = bytes.take(4)
    val skip = start.map(_.toInt).sum

    bytes.slice(skip + 4, skip + 8).map(_.toInt).sum
  }

  def getEventNameFromHexa(serialized: String): String = {
    val bytes: Array[Byte] = parseHexBinary(serialized.substring(initialCharactersToSkip))
    val start = bytes.take(4)
    val skip = start.map(_.toInt).sum
    new String(bytes.slice(4, skip + 4), "UTF-8")
  }

}
