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

class ShardingActorTest(actorSystem: ActorSystem = TestHelper.defaultActorSystem())
    extends TestKit(actorSystem)
    with ImplicitSender
    with MakeUnitTest

object TestHelper {
  val halfNumberOfPorts: Int = 32768
  val counter: AtomicInteger = new AtomicInteger(halfNumberOfPorts)
  val actorSystemName: String = "test_system"
  def configuration: String = {
    val port = counter.getAndIncrement()
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://$actorSystemName@localhost:$port"]
       |  cluster.jmx.multi-mbeans-in-same-jvm = on
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
       |  event-sourcing {
       |    jobs {
       |      journal = $${inmemory-journal}
       |      snapshot = $${inmemory-snapshot-store}
       |    }
       |    proposals {
       |      journal = $${inmemory-journal}
       |      snapshot = $${inmemory-snapshot-store}
       |    }
       |    sequences {
       |      journal = $${inmemory-journal}
       |      snapshot = $${inmemory-snapshot-store}
       |    }
       |    sessions {
       |      journal = $${inmemory-journal}
       |      snapshot = $${inmemory-snapshot-store}
       |    }
       |    users {
       |      journal = $${inmemory-journal}
       |      snapshot = $${inmemory-snapshot-store}
       |    }
       |  }
       |  
       |  kafka {
       |    connection-string = "nowhere:-1"
       |    schema-registry = "http://nowhere:-1"
       |  }
       |
       |  cookie-session.lifetime = "600 milliseconds"
       |  security.secure-hash-salt = "salt-secure"
       |  security.secure-vote-salt = "vote-secure"
       |}
    """.stripMargin
  }

  def fullConfiguration: Config =
    ConfigFactory
      .parseString(configuration)
      .withFallback(ConfigFactory.load("default-application.conf"))
      .resolve()

  def defaultActorSystem(conf: Config = fullConfiguration): ActorSystem = {
    ActorSystem(actorSystemName, conf)
  }

}
