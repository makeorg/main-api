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

package org.make.api.technical.crm
import org.make.api.MakeUnitTest
import io.circe.syntax._

class SendMessagesTest extends MakeUnitTest {

  feature("Sandbox Mode") {
    scenario("send an email to a regular user") {
      val messages = SendMessages(SendEmail(recipients = Seq(Recipient(email = "some-user@gmail.com"))))
      messages.sandboxMode should be(None)
    }
    scenario("send an email to regular users") {
      val messages = SendMessages(
        SendEmail(recipients = Seq(Recipient(email = "some-user@gmail.com"), Recipient(email = "some-user@live.com")))
      )
      messages.sandboxMode should be(None)
    }
    scenario("Send an email to yopmail@make.org") {
      val messages = SendMessages(SendEmail(recipients = Seq(Recipient(email = "yopmail@make.org"))))
      messages.sandboxMode should be(None)
    }
    scenario("Send an email to yopmail+xxx@make.org") {
      val messages = SendMessages(SendEmail(recipients = Seq(Recipient(email = "yopmail+xxx@make.org"))))
      messages.sandboxMode should be(Some(true))
    }
    scenario("Send an email to yopmail+xxx@make.org and another user") {
      val messages = SendMessages(
        SendEmail(recipients = Seq(Recipient(email = "some-user@gmail.com"), Recipient(email = "yopmail+xxx@make.org")))
      )
      messages.sandboxMode should be(None)
    }
    scenario("Send an email to yopmail+xxx@make.org and yopmail+yyy@make.org") {
      val messages = SendMessages(
        SendEmail(recipients = Seq(Recipient(email = "yopmail+xxx@make.org"), Recipient(email = "yopmail+yyy@make.org"))
        )
      )
      messages.sandboxMode should be(Some(true))
    }

  }

  feature("SendMessages serialization") {

    scenario("sandbox mode is active") {
      val messages = SendMessages(SendEmail(recipients = Seq(Recipient(email = "yopmail+xxx@make.org")))).asJson
      val jsons = messages \\ "SandboxMode"
      jsons.isEmpty should be(false)
      jsons.size should be(1)
      jsons.head.as[Boolean] should be(Right(true))

    }
    scenario("sandbox mode is inactive") {
      val messages = SendMessages(SendEmail(recipients = Seq(Recipient(email = "yopmail+xxx@gmail.com")))).asJson
      val jsons = messages \\ "SandboxMode"
      jsons.isEmpty should be(true)
    }

  }

}
