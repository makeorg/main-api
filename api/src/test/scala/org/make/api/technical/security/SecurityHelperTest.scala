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
import org.make.core.proposal.ProposalId
import org.make.core.session.SessionId

class SecurityHelperTest extends MakeUnitTest {

  feature("sha256") {
    scenario("hash with sha512") {
      SecurityHelper
        .hash("testsha512")
        .toUpperCase
        .shouldBe(
          "9B0BEB4EE6AA139B19674E6087D6ACB394F32DA011EBD8E28C3833575F45FBF0329B67D59CBDFEDE50A7BE5841507166BA7FC633F3BDE05E91F6A8F9F297F314"
        )
    }

    scenario("salted hash with sha512") {
      SecurityHelper
        .saltedHash("testsha512", "salt")
        .toUpperCase
        .shouldBe(
          "325615C521B9F94120B722F2DDC594BEFAAA16EC3532DB352745058127DD277BB02F95C6604E6CA8A7655311A42F51A6CC5F0DB88E67B60F9CDC6506A577BDAE"
        )
    }
  }

  feature("proposal key") {
    scenario("generate proposal key") {
      SecurityHelper
        .generateProposalKeyHash(ProposalId("proposal-1"), SessionId("session-1"), Some("location"), "salt")
        .toUpperCase
        .shouldBe(
          "6447130890AFD0C67548771B1CA47254FC9A18878845EC694124F69B78308D82DE7F074197D8E3FF59CBEBFE71C6C9EBEE110D7ABDD5C866EE1E105E4EB00FDD"
        )
    }
  }

}
