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

package org.make.api

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory

class KafkaActorTest(kafkaConnection: String, schemaRegistry: String)
    extends TestKit(KafkaActorTest.defaultActorSystem(KafkaActorTest.configuration(kafkaConnection, schemaRegistry)))
    with MakeUnitTest
    with ImplicitSender

object KafkaActorTest {
  def configuration(kafkaConnection: String, schemaRegistry: String): String =
    s"""
       |akka {
       |
       |  test {
       |    timefactor = 10.0
       |  }
       |}
       |make-api {
       |  kafka {
       |    connection-string = "$kafkaConnection"
       |    poll-timeout = 10000
       |    schema-registry = "$schemaRegistry"
       |    topics {
       |      users = "users"
       |      proposals = "proposals"
       |      mailjet-events = "mailjet-events"
       |    }
       |  }
       |}
    """.stripMargin

  def defaultActorSystem(conf: String): ActorSystem = {
    val system = ActorSystem("test_system", ConfigFactory.parseString(conf))
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    system
  }
}
