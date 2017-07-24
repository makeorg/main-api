package org.make.api

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._

class KafkaActorTest(kafkaConnection: String, schemaRegistry: String)
    extends TestKit(KafkaActorTest.defaultActorSystem(KafkaActorTest.configuration(kafkaConnection, schemaRegistry)))
    with FeatureSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
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
       |      propositions = "propositions"
       |      votes = "votes"
       |      mailjet-events = "mailjet-events"
       |    }
       |  }
       |}
    """.stripMargin

  def defaultActorSystem(conf: String): ActorSystem = {
    val system = ActorSystem("test-system", ConfigFactory.parseString(conf))
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    system
  }
}
