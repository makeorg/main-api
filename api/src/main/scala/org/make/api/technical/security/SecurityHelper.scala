package org.make.api.technical.security
import java.security.MessageDigest
import java.util.Base64

import org.make.core.DateHelper

object SecurityHelper {

  val HASH_SEPARATOR = "-"

  def sha1(value: String): String =
    MessageDigest.getInstance("SHA-1").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

  def sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")).map("%02x".format(_)).mkString

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

  def validateSecureHash(hash: String, value: String, salt: String): Boolean = {
    hash.split(HASH_SEPARATOR) match {
      case Array(base64Date, hashedValue) => generateHash(value, base64Decode(base64Date), salt) == hashedValue
      case _                              => false
    }
  }
}
