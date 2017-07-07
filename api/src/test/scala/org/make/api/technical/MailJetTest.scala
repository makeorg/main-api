package org.make.api.technical

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.make.api.ShardingActorTest
import org.make.api.technical.mailjet.MailJet
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class MailJetTest extends ShardingActorTest with MockitoSugar {

  implicit val materializer = ActorMaterializer()

  feature("transform response") {
    scenario("unmarshall correct responses") {
      val responseString: String =
        """
          |{
          |  "ka": "va",
          |  "kb": "vb"
          |}
        """.stripMargin
      val entity = Strict(ContentTypes.`application/json`, ByteString(responseString))
      val response = HttpResponse(status = StatusCodes.OK, entity = entity)

      whenReady(
        Source
          .single((Try(response), "123456"))
          .via(MailJet.transformResponse[Map[String, String]])
          .runFold(List[Either[Throwable, Map[String, String]]]()) { (acc, result) =>
            result :: acc
          }
          .map(_.head),
        Timeout(3.seconds)
      ) { result =>
        result.isRight should be(true)
        result should be(Right(Map("ka" -> "va", "kb" -> "vb")))
      }
    }
    scenario("return a left if json is invalid") {
      val responseString: String =
        """
          |{
          |  "ka": "va"
          |  "kb": vb
          |}
        """.stripMargin
      val entity = Strict(ContentTypes.`application/json`, ByteString(responseString))
      val response = HttpResponse(status = StatusCodes.OK, entity = entity)

      whenReady(
        Source
          .single((Try(response), "123456"))
          .via(MailJet.transformResponse[Map[String, String]])
          .runFold(List[Either[Throwable, Map[String, String]]]()) { (acc, result) =>
            result :: acc
          }
          .map(_.head),
        Timeout(3.seconds)
      ) { result =>
        result.isLeft should be(true)
      }
    }
    scenario("return a left if json is not of required type") {
      val responseString: String =
        """
          |{
          |  "ka": 1,
          |  "kb":  {
          |    "sub": 42
          |  }
          |}
        """.stripMargin
      val entity = Strict(ContentTypes.`application/json`, ByteString(responseString))
      val response = HttpResponse(status = StatusCodes.OK, entity = entity)

      whenReady(
        Source
          .single((Try(response), "123456"))
          .via(MailJet.transformResponse[Map[String, String]])
          .runFold(List[Either[Throwable, Map[String, String]]]()) { (acc, result) =>
            result :: acc
          }
          .map(_.head),
        Timeout(3.seconds)
      ) { result =>
        result.isLeft should be(true)
      }
    }

    scenario("return a left if http call failed") {
      whenReady(
        Source
          .single((Try(throw new IllegalStateException("fake")), "123456"))
          .via(MailJet.transformResponse[Map[String, String]])
          .runFold(List[Either[Throwable, Map[String, String]]]()) { (acc, result) =>
            result :: acc
          }
          .map(_.head),
        Timeout(3.seconds)
      ) { result =>
        result.isLeft should be(true)
      }
    }
  }

}
