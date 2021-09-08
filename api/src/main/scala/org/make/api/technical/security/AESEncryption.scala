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

import org.make.api.technical.Base64Encoding

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import javax.crypto.Cipher

trait AESEncryptionComponent {
  def aesEncryption: AESEncryption
}

trait AESEncryption {
  def encryptToken(token: Array[Byte]): Array[Byte]
  def decryptToken(buffer: Array[Byte]): Array[Byte]
  def encryptAndEncode(token: String): String
  def decodeAndDecrypt(token: String): String
}

trait DefaultAESEncryptionComponent extends AESEncryptionComponent {
  this: SecurityConfigurationComponent =>

  override lazy val aesEncryption: DefaultAESEncryption = new DefaultAESEncryption
  val random = new SecureRandom()

  class DefaultAESEncryption extends AESEncryption {
    private val EncryptionAlgorithm: String = "AES/GCM/NoPadding"
    private val TagLengthBit: Int = 128
    private val IvSize = 16

    lazy val secretKey: SecretKeySpec = {
      val key = Base64Encoding.decode(securityConfiguration.aesSecretKey)
      new SecretKeySpec(key, "AES")
    }

    override def encryptToken(token: Array[Byte]): Array[Byte] = {
      val cipher: Cipher = Cipher.getInstance(EncryptionAlgorithm)
      val iv = newIV()
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TagLengthBit, iv))
      val encryptedText = cipher.doFinal(token)
      ByteBuffer.allocate(iv.length + encryptedText.length).put(iv).put(encryptedText).array()
    }

    override def decryptToken(token: Array[Byte]): Array[Byte] = {
      val buffer = ByteBuffer.wrap(token)
      val iv = Array.ofDim[Byte](IvSize)
      buffer.get(iv)
      val encryptedText = Array.ofDim[Byte](buffer.remaining())
      buffer.get(encryptedText)
      val cipher: Cipher = Cipher.getInstance(EncryptionAlgorithm)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TagLengthBit, iv))
      cipher.doFinal(encryptedText)
    }

    private def newIV(): Array[Byte] = {
      val buffer = Array.ofDim[Byte](IvSize)
      random.nextBytes(buffer)
      buffer
    }

    override def encryptAndEncode(token: String): String = {
      val encryptedToken = encryptToken(token.getBytes(StandardCharsets.UTF_8))
      Base64Encoding.encode(encryptedToken)
    }

    override def decodeAndDecrypt(token: String): String = {
      val tokenDecoded: Array[Byte] = Base64Encoding.decode(token)
      new String(decryptToken(tokenDecoded), StandardCharsets.UTF_8)
    }
  }
}
