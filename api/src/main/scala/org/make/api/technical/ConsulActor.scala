package org.make.api.technical

import java.nio.charset.Charset
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import org.make.api.extensions.MakeSettingsExtension
import org.make.api.technical.ConsulActor._
import org.make.api.technical.ConsulEntities.{CreateSessionResponse, ReadResponse, RenewSessionResponse, WriteResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

/**
  * Actor used to query consul using akka-http
  */
class ConsulActor extends Actor with ActorLogging with MakeSettingsExtension {

  private val consulUrl: String = settings.Cluster.Consul.httpUrl

  implicit val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(defaultThreadNumber))

  private implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  private val http: HttpExt = Http(context.system)

  override def receive: Receive = {

    case GetKey(key) => retrieveKey(key)
    case CreateSession(ttl) => createSession(ttl)
    case WriteKey(key, value) => writeKey(key, value)
    case WriteExclusiveKey(key, session, value) => writeExclusively(key, value, session)
    case RenewSession(id) => renewSession(id)
    case x => log.warning(s"Unknown message received: ${x.toString}")
  }

  private def renewSession(id: String) = {
    log.debug(s"Renewing session $id")
    pipe(
      http
        .singleRequest(HttpRequest(method = HttpMethods.PUT, uri = s"$consulUrl/v1/session/renew/$id"))
        .flatMap(strictToString(_))
        .map(x => RenewSessionAnswer(RenewSessionResponse.arrayFromJson(x)))
        .recoverWith {
          case x => Future.successful(ConsulFailure("RenewSession", x))
        }
    ).to(sender())
  }

  private def writeExclusively(key: String, value: String, session: String) = {
    log.debug(s"writing $value in $key")
    pipe(
      http
        .singleRequest(
          HttpRequest(
            method = HttpMethods.PUT,
            uri = s"$consulUrl/v1/kv/$key?acquire=$session",
            entity = HttpEntity(ContentTypes.`application/json`, value)
          )
        )
        .flatMap(strictToString(_))
        .map(x => WriteResponse(result = x.trim.toBoolean, key = key, value = value))
        .recoverWith {
          case x => Future.successful(ConsulFailure("WriteExclusiveKey", x))
        }
    ).to(sender())
  }

  private def writeKey(key: String, value: String) = {
    log.debug(s"writing $value in $key")
    pipe(
      http
        .singleRequest(
          HttpRequest(
            method = HttpMethods.PUT,
            uri = s"$consulUrl/v1/kv/$key",
            entity = HttpEntity(ContentTypes.`application/json`, value)
          )
        )
        .flatMap(strictToString(_))
        .map(x => WriteResponse(result = x.trim.toBoolean, key = key, value = value))
        .recoverWith {
          case x => Future.successful(ConsulFailure("WriteKey", x))
        }
    ).to(sender())
  }

  private def createSession(ttl: FiniteDuration): Unit = {
    log.debug(s"Creating session with ttl $ttl")
    val address = Cluster(context.system).selfAddress.toString
    val request =
      s"""
         |{
         |"Name": "Consul session for node $address",
         |"TTL": "${ttl.toSeconds}s"
         |}
        """.stripMargin
    pipe(
      http
        .singleRequest(
          HttpRequest(
            method = HttpMethods.PUT,
            uri = s"$consulUrl/v1/session/create",
            entity = HttpEntity(ContentTypes.`application/json`, request)
          )
        )
        .flatMap(strictToString(_))
        .map(CreateSessionResponse.fromJson)
        .recoverWith {
          case x => Future.successful(ConsulFailure("CreateSession", x))
        }
    ).to(sender())
  }

  private def retrieveKey(key: String): Unit = {
    log.debug(s"reading $key")
    pipe(
      http
        .singleRequest(HttpRequest(uri = s"$consulUrl/v1/kv/$key?raw"))
        .flatMap {
          case HttpResponse(StatusCodes.NotFound, _, _, _) =>
            Future.successful(None)
          case other => strictToString(other).map(Some.apply)
        }
        .map(x => ReadResponse(key = key, value = x))
        .recoverWith {
          case x => Future.successful(ConsulFailure("GetKey", x))
        }
    ).to(sender())
  }

  private def strictToString(response: HttpResponse, expectedCode: StatusCode = StatusCodes.OK): Future[String] = {
    log.debug(s"Server answered $response")
    response match {
      case HttpResponse(`expectedCode`, _, entity, _) =>
        entity
          .toStrict(2.second)
          .map(_.data.decodeString(Charset.forName("UTF-8")))
      case HttpResponse(code, _, entity, _) =>
        entity.toStrict(2.second).flatMap { entity =>
          val response = entity.data.decodeString(Charset.forName("UTF-8"))
          Future.failed(new IllegalStateException(s"Got unexpected response code: $code, with body: $response"))
        }
    }
  }

}

object ConsulActor {

  val name: String = "consul-client"
  val props: Props = Props[ConsulActor]

  val defaultThreadNumber: Int = 5

  case class CreateSession(ttl: FiniteDuration)

  case class RenewSession(id: String)

  case class RenewSessionAnswer(consulObject: Seq[RenewSessionResponse])

  case class GetKey(key: String)

  case class WriteKey(key: String, value: String)

  case class WriteExclusiveKey(key: String, session: String, value: String)

  case class ConsulFailure(operation: String, cause: Throwable)

}

object ConsulEntities extends StrictLogging {

  case class WriteResponse(result: Boolean, key: String, value: String)
  case class ReadResponse(key: String, value: Option[String])
  case class RenewSessionResponse(LockDelay: Float,
                                  Checks: Seq[String],
                                  Node: String,
                                  ID: String,
                                  CreateIndex: Long,
                                  Behavior: String,
                                  TTL: String)

  object RenewSessionResponse {
    def arrayFromJson(json: String): Seq[RenewSessionResponse] = {
      val parsed: Json = parse(json) match {
        case Left(e) => throw e
        case Right(result) => result
      }
      parsed.as[Seq[RenewSessionResponse]] match {
        case Left(e) => throw e
        case Right(result) => result
      }
    }
  }

  case class CreateSessionResponse(ID: String)

  object CreateSessionResponse {
    def fromJson(json: String): CreateSessionResponse = {
      val parsed: Json = parse(json) match {
        case Left(e) => throw e
        case Right(result) => result
      }
      parsed.as[CreateSessionResponse] match {
        case Left(e) => throw e
        case Right(result) => result
      }
    }
  }

  case class NotFound(message: String) extends Exception

}
