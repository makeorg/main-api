package org.make.api.technical.healthcheck

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.make.api.ItMakeTest
import org.make.api.docker.DockerZookeeperService
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus

import scala.concurrent.ExecutionContext

class ZookeeperHealthCheckActorIT
    extends TestKit(ZookeeperHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with ItMakeTest
    with DockerZookeeperService {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
    system.terminate()
  }

  override val zookeeperExposedPort: Int = ZookeeperHealthCheckActorIT.zookeeperExposedPort
  override val zookeeperName: String = "zookeeper-health-check"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout
  feature("Check Zookeeper status") {
    scenario("write current timestamp in zookeeper ephemeral path and read it") {
      Given("a zookeeper health check actor")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckZookeeper: ActorRef = actorSystem.actorOf(
        ZookeeperHealthCheckActor.props(healthCheckExecutionContext),
        ZookeeperHealthCheckActor.name
      )

      When("I send a message to check the status of zookeeper")
      healthCheckZookeeper ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("zookeeper", "OK"))
    }
  }
}

object ZookeeperHealthCheckActorIT {
  val zookeeperExposedPort = 12182
  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    s"""
      |akka.log-dead-letters-during-shutdown = off
      |make-api.zookeeper.url = "localhost:$zookeeperExposedPort"
    """.stripMargin

  val actorSystem = ActorSystem("ZookeeperHealthCheckActorIT", ConfigFactory.parseString(configuration))

}
