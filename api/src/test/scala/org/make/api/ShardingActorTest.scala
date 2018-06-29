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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._

class ShardingActorTest(actorSystem: ActorSystem = TestHelper.defaultActorSystem())
    extends TestKit(actorSystem)
    with FeatureSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender

object TestHelper {
  val halfNumberOfPorts: Int = 32768
  private val counter = new AtomicInteger(halfNumberOfPorts)
  def configuration: String = {
    val port = counter.getAndIncrement()
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://test-system@localhost:$port"]
       |
       |  persistence {
       |    journal.plugin = "inmemory-journal"
       |    snapshot-store.plugin = "inmemory-snapshot-store"
       |  }
       |
       |  remote.artery.canonical {
       |    port = $port
       |    hostname = "localhost"
       |  }
       |
       |  test {
       |    timefactor = 10.0
       |  }
       |}
       |make-api {
       |  kafka {
       |    connection-string = "nowhere:-1"
       |    schema-registry = "http://nowhere:-1"
       |  }
       |}
    """.stripMargin
  }

  def fullConfiguration: Config =
    ConfigFactory.parseString(configuration).withFallback(ConfigFactory.load("default-application.conf"))

  def defaultActorSystem(conf: Config = fullConfiguration): ActorSystem = {
    ActorSystem("test-system", conf)
  }

}
