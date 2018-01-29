package org.make.api

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
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
  def configuration: String =
    s"""
      |akka {
      |
      |  remote.netty.tcp.port = ${counter.getAndIncrement()}
      |
      |  actor {
      |    provider = "akka.cluster.ClusterActorRefProvider"
      |
      |    serializers {
      |      make-serializer = "org.make.api.technical.MakeEventSerializer"
      |    }
      |    serialization-bindings {
      |      "org.make.core.MakeSerializable" = make-serializer
      |    }
      |  }
      |
      |  persistence {
      |    journal.plugin = "inmemory-journal"
      |    snapshot-store.plugin = "inmemory-snapshot-store"
      |  }
      |
      |  cluster {
      |     sharding {
      |      guardian-name = sharding
      |      remember-entities = on
      |      state-store-mode = persistence
      |      snapshot-plugin-id = "inmemory-snapshot-store"
      |      journal-plugin-id = "inmemory-journal"
      |    }
      |  }
      |
      |  test {
      |    timefactor = 10.0
      |  }
      |}
      |make-api {
      |  kafka {
      |    connection-string = "nowhere:-1"
      |    poll-timeout = 10000
      |    schema-registry = "http://nowhere:-1"
      |    topics {
      |      users = "users"
      |      proposals = "proposals"
      |      emails = "emails"
      |      mailjet-events = "mailjet-events"
      |    }
      |  }
      |}
    """.stripMargin

  def defaultActorSystem(conf: String = configuration): ActorSystem = {
    val system = ActorSystem("test-system", ConfigFactory.parseString(conf))
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    system
  }

}
