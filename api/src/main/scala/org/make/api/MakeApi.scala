package org.make.api

import akka.actor.ActorSystem
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import org.make.api.citizen.{CitizenActors, CitizenApi, CitizenServiceComponent}
import SerializationPredef._

object MakeApi extends TwitterServer
  with CitizenServiceComponent
  with IdGeneratorComponent
  with CitizenApi {

  private val actorSystem = ActorSystem("make-app")
  private val citizenCoordinator = actorSystem.actorOf(CitizenActors.props, CitizenActors.name)

  override def idGenerator: MakeApi.IdGenerator = new UUIDIdGenerator
  override def citizenService: MakeApi.CitizenService = new CitizenService(citizenCoordinator)


  def main(): Unit = {
    val api: Service[Request, Response] = {
      citizenOperations.handle({
        case e: Exception => Output.failure(e)
      }).toServiceAs[Application.Json]
    }

    val server: ListeningServer = Http.server.serve("0.0.0.0:9000", api)
    onExit {
      server.close()
    }
    Await.ready(adminHttpServer)
  }

}
