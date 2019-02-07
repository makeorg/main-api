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

package org.make.api.technical.security

import java.security.MessageDigest
import java.util.Base64

import org.make.core.DateHelper
import org.make.core.proposal.ProposalId
import org.make.core.session.SessionId

object SecurityHelper {

  val HASH_SEPARATOR = "-"

  def sha1(value: String): String =
    MessageDigest.getInstance("SHA-1").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def hash(value: String): String =
    MessageDigest.getInstance("SHA-512").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def base64Encode(value: String): String =
    Base64.getEncoder.encodeToString(value.getBytes("UTF-8"))

  def base64Decode(value: String): String =
    new String(Base64.getDecoder.decode(value), "UTF-8")

  def generateHash(value: String, date: String, salt: String): String =
    sha256(s"${sha256(value)}$date$salt")

  def createSecureHash(value: String, salt: String): String = {
    val date = DateHelper.now().toString

    s"${base64Encode(date)}$HASH_SEPARATOR${generateHash(value, date, salt)}"
  }

  def saltedHash(value: String, salt: String): String =
    hash(s"$value$salt")

  def generateProposalKeyHash(proposalId: ProposalId,
                              sessionId: SessionId,
                              location: Option[String],
                              salt: String): String = {
    val rowString: String = s"${proposalId.value}${sessionId.value}${location.getOrElse("")}"
    saltedHash(hash(rowString), salt)
  }

  def validateSecureHash(hash: String, value: String, salt: String): Boolean = {
    hash.split(HASH_SEPARATOR) match {
      case Array(base64Date, hashedValue) => generateHash(value, base64Decode(base64Date), salt) == hashedValue
      case _                              => false
    }
  }
}
