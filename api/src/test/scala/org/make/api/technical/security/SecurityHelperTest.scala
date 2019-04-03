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

import org.make.api.MakeUnitTest
import org.make.api.technical.security.SecurityHelper.{base64Encode, HASH_SEPARATOR}
import org.make.core.proposal.ProposalId
import org.make.core.session.SessionId

class SecurityHelperTest extends MakeUnitTest {

  feature("sha512") {
    scenario("hash with sha512") {
      SecurityHelper
        .defaultHash("testsha512")
        .shouldBe(
          "9b0beb4ee6aa139b19674e6087d6acb394f32da011ebd8e28c3833575f45fbf0329b67d59cbdfede50a7be5841507166ba7fc633f3bde05e91f6a8f9f297f314"
        )
    }

    scenario("salted hash with sha512") {
      SecurityHelper
        .generateHash("testsha512", salt = "salt")
        .shouldBe(
          "e3ece0b90047c01a802ec692be1e24dfcf854cbdf2e3cf493878d505cd88effb83ceaedf369d737748c17c583d08ffeefe52daf87739a649ddd327c3486d47a7"
        )
    }

    scenario("base64 ") {
      SecurityHelper
        .base64Encode("testbase64")
        .shouldBe("dGVzdGJhc2U2NA==")
    }
  }

  feature("proposal key") {
    scenario("generate proposal key") {
      SecurityHelper
        .generateProposalKeyHash(ProposalId("proposal-1"), SessionId("session-1"), Some("location"), "salt")
        .shouldBe(
          "6447130890afd0c67548771b1ca47254fc9a18878845ec694124f69b78308d82de7f074197d8e3ff59cbebfe71c6c9ebee110d7abdd5c866ee1e105e4eb00fdd"
        )
    }
  }

  feature("backward compatibility hashing") {
    scenario("sha256") {
      SecurityHelper
        .sha256("testsha256")
        .shouldBe("ad6c2d91c3bc6772e312d63d0e0528518580835685a653503df38173739d65b3")
    }

    scenario("validate both deprecated and default hashes") {
      val value = "test"
      val salt = "salt"
      val base64 = "base64"
      val deprecatedHash =
        s"${base64Encode(base64)}$HASH_SEPARATOR${SecurityHelper.sha256(s"${SecurityHelper.sha256(s"$value")}$base64$salt")}"
      val hash = s"${base64Encode(base64)}$HASH_SEPARATOR${SecurityHelper.generateHash(s"$value$base64", salt)}"

      SecurityHelper.validateSecureHash(deprecatedHash, value, salt) shouldBe true
      SecurityHelper.validateSecureHash(hash, value, salt) shouldBe true
      SecurityHelper.validateSecureHash("invalid-hash", value, salt) shouldBe false
    }
  }
}
