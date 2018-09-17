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
import java.nio.ByteOrder

import javax.xml.bind.DatatypeConverter
import org.make.api.technical.MakeEventSerializer
import stamina.ByteString

object StaminaTestUtils {
  private val initialCharactersToSkip: Int = 2

  def deserializeEventFromJson[A](eventKey: String, eventAsJsonString: String): A = {
    implicit val byteOrder: ByteOrder = java.nio.ByteOrder.LITTLE_ENDIAN
    val makeEventSerializer: MakeEventSerializer = new MakeEventSerializer()
    val bytes: Array[Byte] = ByteString.newBuilder
      .putInt(eventKey.length)
      .putBytes(eventKey.getBytes("UTF-8"))
      .putInt(1)
      .append(ByteString(eventAsJsonString))
      .result
      .toArray

    makeEventSerializer.fromBinary(bytes).asInstanceOf[A]
  }

  def deserializeEventFromHexa[A](serialized: String): A = {
    val makeEventSerializer: MakeEventSerializer = new MakeEventSerializer()

    val bytes: Array[Byte] = DatatypeConverter.parseHexBinary(serialized.substring(initialCharactersToSkip))

    makeEventSerializer.fromBinary(bytes).asInstanceOf[A]
  }

  def getVersionFromHexa(serialized: String): Int = {
    val bytes: Array[Byte] = DatatypeConverter.parseHexBinary(serialized.substring(initialCharactersToSkip))
    val start = bytes.take(4)
    val skip = start.map(_.toInt).sum

    bytes.slice(skip + 4, skip + 8).map(_.toInt).sum
  }

  def getEventNameFromHexa(serialized: String): String = {
    val bytes: Array[Byte] = DatatypeConverter.parseHexBinary(serialized.substring(initialCharactersToSkip))
    val start = bytes.take(4)
    val skip = start.map(_.toInt).sum
    new String(bytes.slice(4, skip + 4), "UTF-8")
  }

}
