/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.crm

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

object arbitraries {

  private final case class Email(value: String)

  private implicit val arbEmail: Arbitrary[Email] = Arbitrary(for {
    name   <- Gen.identifier
    domain <- Gen.identifier
    tld    <- Gen.oneOf("com", "org", "io")
  } yield Email(s"$name@$domain.$tld"))

  implicit val arbRecipient: Arbitrary[Recipient] = Arbitrary(for {
    email     <- arbitrary[Email]
    name      <- arbitrary[Option[String]]
    variables <- arbitrary[Option[Map[String, String]]]
  } yield Recipient(email.value, name, variables))

  implicit val arbSendEmail: Arbitrary[SendEmail] = Arbitrary(for {
    id         <- arbitrary[String]
    from       <- arbitrary[Option[Recipient]]
    subject    <- arbitrary[Option[String]]
    textPart   <- arbitrary[Option[String]]
    recipients <- Gen.listOf(arbitrary[Recipient])
  } yield SendEmail(id = id, from = from, subject = subject, textPart = textPart, recipients = recipients))

}
