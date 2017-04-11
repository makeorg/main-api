package org.make.api

import java.time.LocalDate

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import io.circe._
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import org.make.api.citizen.CitizenApi
import org.make.core.citizen.CitizenId

object MakeApi extends TwitterServer {

 // implicit val decodeCitizenId: DecodeEntity[StringValue] = DecodeEntity.instance{ s => Try(CitizenId(s))}
  implicit val encodeCitizenId: Encoder[CitizenId] = Encoder.instance[CitizenId] { citizenId => Json.fromString(citizenId.value)}
  implicit val encodeLocalDate: Encoder[LocalDate] = Encoder.instance[LocalDate] { date => Json.fromString(date.toString)}

  case class MyEntity(value: String)

  def method1: Endpoint[String] = get("hello" :: string) { name: String =>
    Ok(s"Hello, $name!")
  }

  def method2: Endpoint[MyEntity] = post("toto" :: jsonBody[MyEntity]) { body: MyEntity =>
    Ok(body)
  }


  val api: Service[Request, Response] = {
    (method1 :+: method2 :+: CitizenApi.getCitizen).handle({
      case e: Exception => Output.failure(e)
    }).toServiceAs[Application.Json]
  }



  def main(): Unit = {
    val server: ListeningServer = Http.server.serve("0.0.0.0:9000", api)
    onExit { server.close() }
    Await.ready(adminHttpServer)
  }


}
