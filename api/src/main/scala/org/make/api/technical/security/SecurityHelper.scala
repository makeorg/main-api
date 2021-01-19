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

import grizzled.slf4j.Logging
import org.make.core.DateHelper
import org.make.core.proposal.ProposalId
import org.make.core.session.SessionId

object SecurityHelper extends Logging {

  val HASH_SEPARATOR = "-"

  @Deprecated
//  This hash method is used for backward compatibility only. Use `defaultHash` instead.
  def sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def defaultHash(value: String): String =
    MessageDigest.getInstance("SHA-512").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def base64Encode(value: String): String =
    Base64.getEncoder.encodeToString(value.getBytes("UTF-8"))

  def base64Decode(value: String): String =
    new String(Base64.getDecoder.decode(value), "UTF-8")

  def generateHash(value: String, salt: String): String =
    defaultHash(s"${defaultHash(value)}$salt")

  def createSecureHash(value: String, salt: String): String = {
    val date = DateHelper.now().toString

    s"${base64Encode(date)}$HASH_SEPARATOR${generateHash(s"$value$date", salt)}"
  }

  def validateSecureHash(hash: String, value: String, salt: String): Boolean = {
    hash.split(HASH_SEPARATOR) match {
      case Array(base64Date, hashedValue) =>
        val deprecatedCheck = sha256(s"${sha256(s"$value")}${base64Decode(base64Date)}$salt") == hashedValue
        if (deprecatedCheck) {
          logger.warn(s"Use of deprecated hash function (sha256) on value $value")
        }
        generateHash(s"$value${base64Decode(base64Date)}", salt) == hashedValue || deprecatedCheck
      case _ => false
    }
  }

  def generateProposalKeyHash(
    proposalId: ProposalId,
    sessionId: SessionId,
    location: Option[String],
    salt: String
  ): String = {
    val rawString: String = s"${proposalId.value}${sessionId.value}${location.getOrElse("")}"
    generateHash(value = rawString, salt = salt)
  }

  def anonymizeEmail(email: String): String = {
    val emailDomainName = email.takeRight(email.length - email.lastIndexOf("@"))
    val emailLocalPart = email.substring(0, email.lastIndexOf("@"))
    s"${emailLocalPart.head}${"*" * (emailLocalPart.length - 2)}${emailLocalPart.last}$emailDomainName"
  }
}
