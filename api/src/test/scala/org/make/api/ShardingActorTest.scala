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
