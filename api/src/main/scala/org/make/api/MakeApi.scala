package org.make.api

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

object MakeApi extends TwitterServer {

  case class MyEntity(value: String)

  def method1: Endpoint[String] = get("hello" :: string) { name: String =>
    Ok("Yeay !")
  }

  def method2: Endpoint[MyEntity] = post("toto" :: jsonBody[MyEntity]) { body: MyEntity =>
    Ok(body)
  }

  val api: Service[Request, Response] = (method1 :+: method2).handle({
    case e: Exception => Output.failure(e)
  }).toServiceAs[Application.Json]



  def main(): Unit = {
    val server: ListeningServer = Http.server.serve("0.0.0.0:9000", api)
    onExit { server.close() }
    Await.ready(adminHttpServer)
  }

}
