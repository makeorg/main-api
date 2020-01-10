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

package org.make.api.technical.storage
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.api.docker.DockerSwiftAllInOne
import org.make.api.technical.storage.SwiftClientTest.bucket
import org.make.swift.model.Bucket
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

class SwiftClientTest
    extends ItMakeTest
    with DockerSwiftAllInOne
    with DefaultSwiftClientComponent
    with ActorSystemComponent {

  override def externalPort: Option[Int] = Some(SwiftClientTest.port)
  override def actorSystem: ActorSystem = SwiftClientTest.system

  // This test ensures there is no integration issue with the swift client
  // !! Production code will use keystone V2 !!
  feature("contacting swift") {
    ignore("all-in-one") {

      val resolvedBucket = Bucket(0, 0, bucket)
      whenReady(
        swiftClient
          .sendFile(resolvedBucket, "test.json", "application/json", """{"test": true}""".getBytes("UTF-8")),
        Timeout(30.seconds)
      ) { _ =>
        ()
      }

      whenReady(swiftClient.listFiles(resolvedBucket), Timeout(30.seconds)) { files =>
        files.size should be(1)
        files.head.name should be("test.json")
      }

    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(swiftClient.init(), atMost = 30.seconds)
  }

}

object SwiftClientTest {

  val port: Int = 60000
  val bucket: String = "assets"
  val configuration: String =
    s"""
       |make-openstack {
       |  authentication {
       |    keystone-version = "keystone-V1"
       |    base-url = "http://localhost:$port/auth/v1.0"
       |    tenant-name = "test"
       |    username = "tester"
       |    password = "testing"
       |  }
       |
       |  storage {
       |    init-containers = ["$bucket"]
       |  }
       |}
     """.stripMargin

  val system: ActorSystem = {
    val config = ConfigFactory.load(ConfigFactory.parseString(configuration))
    ActorSystem(classOf[SwiftClientTest].getSimpleName, config)
  }

}
