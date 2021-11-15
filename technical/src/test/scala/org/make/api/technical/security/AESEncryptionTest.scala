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

import grizzled.slf4j.Logging
import org.make.core.BaseUnitTest

import scala.util.Success

class AESEncryptionTest
    extends BaseUnitTest
    with DefaultAESEncryptionComponent
    with SecurityConfigurationComponent
    with Logging {

  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]

  when(securityConfiguration.aesSecretKey).thenReturn("G9pPOCayHYlBnNAq1mCVqA==")

  Feature("AES") {
    Scenario("encrypt/decrypt") {
      val token = "string-to-encode"
      val token2 = "another-string-to-encode"
      val base64Regex =
        "([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)"

      val tokenEncrypted = aesEncryption.encryptAndEncode(token)
      tokenEncrypted.should(fullyMatch.regex(base64Regex))
      aesEncryption.decodeAndDecrypt(tokenEncrypted).shouldBe(Success(token))

      val token2Encrypted = aesEncryption.encryptAndEncode(token2)
      token2Encrypted.should(fullyMatch.regex(base64Regex))
      aesEncryption.decodeAndDecrypt(token2Encrypted).shouldBe(Success(token2))
    }
  }
}
