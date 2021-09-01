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

  class DefaultAESEncryption extends AESEncryption {
    private val ENCRYPT_ALGO: String = "AES/GCM/NoPadding"
    private val TAG_LENGTH_BIT: Int = 128
    private val iv: Array[Byte] = securityConfiguration.aesInitialVector.getBytes(StandardCharsets.UTF_8)

    lazy val getAESKey: SecretKeySpec = {
      val secretKey = securityConfiguration.aesSecretKey
      new SecretKeySpec(secretKey.getBytes, 0, secretKey.length, "AES")
    }

    override def encryptToken(token: Array[Byte]): Array[Byte] = {
      val cipher: Cipher = Cipher.getInstance(ENCRYPT_ALGO)
      cipher.init(Cipher.ENCRYPT_MODE, getAESKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv))
      val encryptedText = cipher.doFinal(token)
      ByteBuffer.allocate(iv.length + encryptedText.length).put(iv).put(encryptedText).array()
    }

    override def decryptToken(token: Array[Byte]): Array[Byte] = {
      val buffer = ByteBuffer.wrap(token)
      val iv = new Array[Byte](securityConfiguration.aesInitialVector.getBytes.length)
      buffer.get(iv)
      val encrytedText = new Array[Byte](buffer.remaining())
      buffer.get(encrytedText)
      val cipher: Cipher = Cipher.getInstance(ENCRYPT_ALGO)
      cipher.init(Cipher.DECRYPT_MODE, getAESKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv))
      cipher.doFinal(encrytedText)
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
