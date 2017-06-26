package org.make.api.technical

import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.make.api.ShardingActorTest
import org.make.api.technical.mailjet.MailJet
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class MailJetTest extends ShardingActorTest with MockitoSugar {

  implicit val materializer = ActorMaterializer()

  "transformResponse" should {
    "unmarshall correct responses" in {
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
          .map(_.head)
      ) { result =>
        result.isRight should be(true)
        result should be(Right(Map("ka" -> "va", "kb" -> "vb")))
      }
    }

    "return a left if json is invalid" in {
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
          .map(_.head)
      ) { result =>
        result.isLeft should be(true)
      }
    }

    "return a left if json is not of required type" in {
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
          .map(_.head)
      ) { result =>
        result.isLeft should be(true)
      }
    }

    "return a left if http call failed" in {

      whenReady(
        Source
          .single((Try(throw new IllegalStateException("fake")), "123456"))
          .via(MailJet.transformResponse[Map[String, String]])
          .runFold(List[Either[Throwable, Map[String, String]]]()) { (acc, result) =>
            result :: acc
          }
          .map(_.head)
      ) { result =>
        result.isLeft should be(true)
      }
    }

  }

}
