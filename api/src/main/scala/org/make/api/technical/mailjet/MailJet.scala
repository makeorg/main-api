package org.make.api.technical.mailjet

import java.net.URL
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Zip}
import akka.stream.{ActorMaterializer, FlowShape, Graph}
import akka.{Done, NotUsed}
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, Printer}
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.extensions.KafkaConfiguration
import org.make.api.technical.mailjet.SendEmail.SendResult

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object MailJet extends StrictLogging {

  private val identityMapCapacity = 1000

  def consumerSettings(actorSystem: ActorSystem): ConsumerSettings[String, AnyRef] = {
    val kafkaConfiguration = KafkaConfiguration(actorSystem)
    ConsumerSettings(
      actorSystem,
      new StringDeserializer,
      new KafkaAvroDeserializer(new CachedSchemaRegistryClient(kafkaConfiguration.schemaRegistry, identityMapCapacity))
    ).withBootstrapServers(kafkaConfiguration.connectionString)
      .withGroupId("stream-send-email")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
  }

  val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)

  type FlowGraph =
    Graph[FlowShape[CommittableMessage[String, AnyRef], Done], NotUsed]

  private def prepareSendEmailRequest(url: URL,
                                      login: String,
                                      password: String): Flow[SendEmail, (HttpRequest, String), NotUsed] =
    Flow[SendEmail].map { sendEmailRequest =>
      (
        HttpRequest(
          method = HttpMethods.POST,
          uri = url.toExternalForm,
          entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(sendEmailRequest.asJson)),
          headers = immutable.Seq(Authorization(BasicHttpCredentials(login, password)))
        ),
        UUID.randomUUID().toString
      )
    }

  private def httpPool(url: URL)(
    implicit system: ActorSystem,
    materializer: ActorMaterializer
  ): Flow[(HttpRequest, String), (Try[HttpResponse], String), Http.HostConnectionPool] = {
    if (url.getProtocol.toLowerCase() == "https") {
      Http().cachedHostConnectionPoolHttps[String](url.getHost)
    } else {
      Http().cachedHostConnectionPool[String](url.getHost)
    }

  }

  /**
    * Generic method to unmarshall http responses to a given type
    *
    * @param materializer     a way to materialize the stream
    * @param executionContext the execution context to use in order "strictify" the requests
    * @param decoder          a decoder used to unmarshall the stream
    * @tparam T the desired return type in the stream
    * @return a stream transforming the response in the given type
    */
  def transformResponse[T](implicit materializer: ActorMaterializer,
                           executionContext: ExecutionContext,
                           decoder: Decoder[T]): Flow[(Try[HttpResponse], String), Either[Throwable, T], NotUsed] = {
    Flow[(Try[HttpResponse], String)]
      .mapAsync[Either[Throwable, Strict]](1) {
        case (Success(response), _) =>
          response.entity
            .toStrict(2.seconds)
            .map(entity => Right(entity))
            .recoverWith {
              case e => Future.successful(Left(e))
            }
        case (Failure(e), _) => Future.successful(Left(e))
      }
      .map[Either[Throwable, Json]] {
        case Right(entity) => parse(entity.data.decodeString("UTF-8"))
        case Left(e)       => Left(e)
      }
      .map[Either[Throwable, T]] {
        case Right(json) => json.as[T]
        case Left(e)     => Left(e)
      }
  }

  def createFlow(url: URL, login: String, password: String)(
    implicit system: ActorSystem,
    materializer: ActorMaterializer,
    executionContext: ExecutionContext
  ): Flow[SendEmail, Either[Throwable, SendResult], NotUsed] = {
    prepareSendEmailRequest(url, login, password).via(httpPool(url)).via(transformResponse[SendResult])
  }

  // TODO duplicated from org.make.api.proposition.PropositionStreamToElasticsearchComponent
  val commitOffset: Flow[(CommittableMessage[String, AnyRef], Either[Throwable, _]), Done, NotUsed] =
    Flow[(CommittableMessage[String, AnyRef], Either[Throwable, _])]
      .map[CommittableOffset] {
        case (message, Right(_)) => message.committableOffset
        case (_, Left(e))        => throw e
      }
      //        WIP:
      //        .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
      //          batch.updated(elem)
      //        }
      .mapAsync(1)(_.commitScaladsl())

  val recordToEvent: Flow[CommittableMessage[String, AnyRef], SendEmail, NotUsed] =
    Flow[ConsumerMessage.CommittableMessage[String, AnyRef]].map { msg =>
      RecordFormat[SendEmail].from(msg.record.value.asInstanceOf[GenericRecord])
    }

  def push(url: URL, login: String, password: String)(implicit system: ActorSystem,
                                                      materializer: ActorMaterializer,
                                                      executionContext: ExecutionContext): FlowGraph = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      val bcast = builder.add(Broadcast[CommittableMessage[String, AnyRef]](2))

      val zip = builder.add(Zip[CommittableMessage[String, AnyRef], Either[Throwable, SendResult]]())
      bcast ~> zip.in0
      bcast ~> recordToEvent ~> createFlow(url, login, password) ~> zip.in1

      FlowShape(bcast.in, (zip.out ~> commitOffset).outlet)
    })
  }

  def run(url: URL, login: String, password: String)(implicit system: ActorSystem,
                                                     materializer: ActorMaterializer,
                                                     executionContext: ExecutionContext): Future[Done] = {
    Consumer
      .committableSource(
        consumerSettings(system),
        Subscriptions.topics(KafkaConfiguration(system).topics(MailJetProducerActor.topicKey))
      )
      .via(push(url, login, password))
      .runWith(Sink.foreach(msg => logger.info("Streaming of one message: {}", msg.toString)))
  }

}

case class SendEmail(fromEmail: Option[String] = None,
                     fromName: Option[String] = None,
                     subject: Option[String] = None,
                     textPart: Option[String] = None,
                     htmlPart: Option[String] = None,
                     useTemplateLanguage: Option[Boolean] = Some(true),
                     templateId: Option[String] = None,
                     variables: Option[Map[String, String]] = None,
                     recipients: Seq[Recipient],
                     headers: Option[Map[String, String]] = None,
                     emailId: Option[String] = None)

object SendEmail {
  implicit val encoder: Encoder[SendEmail] = Encoder.forProduct11(
    "FromEmail",
    "FromName",
    "Subject",
    "Text-part",
    "Html-part",
    "MJ-TemplateLanguage",
    "MJ-TemplateID",
    "Vars",
    "Recipients",
    "Headers",
    "Mj-CustomID"
  ) { sendEmail =>
    (
      sendEmail.fromEmail,
      sendEmail.fromName,
      sendEmail.subject,
      sendEmail.textPart,
      sendEmail.htmlPart,
      sendEmail.useTemplateLanguage,
      sendEmail.templateId,
      sendEmail.variables,
      sendEmail.recipients,
      sendEmail.headers,
      sendEmail.emailId
    )
  }

  case class SendResult(sent: Seq[EmailDetail])

  object SendResult {
    implicit val decoder: Decoder[SendResult] = Decoder.forProduct1("Sent")(SendResult.apply)
  }

  case class EmailDetail(email: String, messageId: Long)

  object EmailDetail {
    implicit val decoder: Decoder[EmailDetail] = Decoder.forProduct2("Email", "MessageID")(EmailDetail.apply)
  }

}

case class Recipient(email: String, name: Option[String] = None, variables: Map[String, String] = Map())

object Recipient {
  implicit val encoder: Encoder[Recipient] = Encoder.forProduct3("Email", "Name", "Vars") { recipient =>
    (recipient.email, recipient.name, recipient.variables)
  }
}
