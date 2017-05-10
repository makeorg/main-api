package org.make.api

import java.time.ZonedDateTime
import java.util.concurrent.Executors

import akka.NotUsed
import akka.actor.{ActorSystem, Extension}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.auth.{MakeDataHandlerComponent, TokenServiceComponent}
import org.make.api.citizen.{CitizenApi, CitizenServiceComponent, PersistentCitizenServiceComponent}
import org.make.api.database.DatabaseConfiguration
import org.make.api.elasticsearch.{ElasticsearchAPI, PropositionElasticsearch}
import org.make.api.kafka.ConsumerActor.Consume
import org.make.api.kafka._
import org.make.api.proposition.{PropositionApi, PropositionCoordinator, PropositionServiceComponent}
import org.make.api.swagger.MakeDocumentation
import org.make.core.citizen.CitizenEvent.CitizenEventWrapper
import org.make.core.proposition.PropositionEvent.PropositionEventWrapper
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteEvent.VoteEventWrapper
import scalikejdbc.{GlobalSettings, LoggingSQLAndTimeSettings}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success}
import scalaoauth2.provider._

object MakeApi extends App
  with CitizenServiceComponent
  with IdGeneratorComponent
  with PersistentCitizenServiceComponent
  with CitizenApi
  with PropositionServiceComponent
  with PropositionApi
  with MakeDataHandlerComponent
  with TokenServiceComponent
  with RequestTimeout
  with AvroSerializers
  with StrictLogging {

  implicit val ctx: EC = ECGlobal

  val swagger =
    path("swagger") {
      getFromResource("META-INF/resources/webjars/swagger-ui/2.2.8/index.html")
    } ~
      getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/2.2.8")

  val login = path("login.html") {
    getFromResource("auth/login.html")
  }

  private implicit val actorSystem = ActorSystem("make-api")
  actorSystem.registerExtension(DatabaseConfiguration)

  private val propositionCoordinator = actorSystem.actorOf(PropositionCoordinator.props, PropositionCoordinator.name)
  override val idGenerator: IdGenerator = new UUIDIdGenerator
  override val citizenService: CitizenService = new CitizenService()
  override val propositionService: PropositionService = new PropositionService(propositionCoordinator)
  override val persistentCitizenService: PersistentCitizenService = new PersistentCitizenService()
  override val oauth2DataHandler: MakeDataHandler = new MakeDataHandler()
  override val tokenService: TokenService = new TokenService()

  override def readExecutionContext: EC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(50))
  override def writeExecutionContext: EC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))

  override val tokenEndpoint: TokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.IMPLICIT -> new Implicit,
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
      OAuthGrantType.PASSWORD -> new Password,
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
    )
  }

  GlobalSettings.loggingSQLErrors = true
  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = true,
    warningEnabled = false,
    printUnprocessedStackTrace = false,
    logLevel = 'info
  )

  val config = actorSystem.settings.config
  val settings = new MakeSettings(actorSystem.settings.config)

  if (settings.useEmbeddedElasticSearch) {
    org.make.api.EmbeddedApplication.embeddedElastic.start()
  }

  val host = settings.http.host
  val port = settings.http.port

  implicit val ec = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer()

  val apiTypes: Seq[ru.Type] = Seq(ru.typeOf[CitizenApi], ru.typeOf[PropositionApi])
  val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(
    new MakeDocumentation(actorSystem, apiTypes).routes ~
      swagger ~
      login ~
      citizenRoutes ~
      propositionRoutes ~
      accessTokenRoute,
    host, port)


  val citizenProducer = actorSystem.actorOf(CitizenProducerActor.props, CitizenProducerActor.name)
  val citizenConsumer = actorSystem.actorOf(ConsumerActor.props(RecordFormat[CitizenEventWrapper], "citizens"), "citizens-" + ConsumerActor.name)

  val propositionProducer = actorSystem.actorOf(PropositionProducerActor.props, PropositionProducerActor.name)
  val propositionConsumer = actorSystem.actorOf(ConsumerActor.props(RecordFormat[PropositionEventWrapper], "propositions"), "propositions-" + ConsumerActor.name)

  val voteProducer = actorSystem.actorOf(VoteProducerActor.props, VoteProducerActor.name)
  val voteConsumer = actorSystem.actorOf(ConsumerActor.props(RecordFormat[VoteEventWrapper], "votes"), "votes-" + ConsumerActor.name)

  val deadLettersListener = actorSystem.actorOf(DeadLettersListenerActor.props, DeadLettersListenerActor.name)

  citizenConsumer ! Consume

  //EXPERIMENTAL --> test integration
  propositionConsumer ! Consume

  logger.debug("Proposing...")
  val propId: PropositionId = Await.result(propositionService
    .propose(idGenerator.nextCitizenId(),ZonedDateTime.now, "Il faut faire une proposition"), Duration.Inf) match {
      case Some(proposition) => proposition.propositionId
      case None => PropositionId("Invalid PropositionId")
    }

  case class AkkaStreamKafkaRecord(record: PropositionElasticsearch, committableOffset: CommittableOffset)

  val client = new CachedSchemaRegistryClient(KafkaConfiguration(actorSystem).schemaRegistry,1000)
  val propositionConsumerSettings = ConsumerSettings(actorSystem, new StringDeserializer, new KafkaAvroDeserializer(client))
    .withBootstrapServers(KafkaConfiguration(actorSystem).connectionString)
    .withGroupId("stream-proposition-to-es")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def shape(msg: CommittableMessage[String, AnyRef]): Future[Option[AkkaStreamKafkaRecord]] = {
      logger.debug("Flowing through shape")
      logger.debug(msg.record.toString)
      PropositionElasticsearch.shape(
        RecordFormat[PropositionEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
      ) map {
        case Some(propositionElasticsearch) =>
          logger.debug("Shaped as propositionElasticsearch:")
          logger.debug(propositionElasticsearch.toString)
          Some(AkkaStreamKafkaRecord(
            propositionElasticsearch,
            msg.committableOffset
          ))
        case _ =>
          logger.debug("Shaped as None ...")
          None
      }
    }

  val filter: Flow[Option[AkkaStreamKafkaRecord], AkkaStreamKafkaRecord, NotUsed] =
    Flow[Option[AkkaStreamKafkaRecord]].filter(_.isDefined).map(_.get)

  val esPush: Flow[AkkaStreamKafkaRecord, CommittableOffset, NotUsed] = {
    Flow[AkkaStreamKafkaRecord].map { committableRecord =>
      logger.debug("Stream shaped")
      logger.debug(committableRecord.record.toString)
      //Condition not perfect...
      if (committableRecord.record.createdAt == committableRecord.record.updatedAt)
        ElasticsearchAPI.api.save(committableRecord.record)
      else
        ElasticsearchAPI.api.updateProposition(committableRecord.record)
      committableRecord.committableOffset
    }
  }

  val runnableGraph =
    Consumer.committableSource(propositionConsumerSettings, Subscriptions.topics(PropositionProducerActor.kafkaTopic(actorSystem)))
      .mapAsync(1)(shape)
      .via(filter)
      .via(esPush)
      .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        batch.updated(elem)
      }
      .mapAsync(3)(_.commitScaladsl())
      .runWith(Sink.foreach(msg => logger.info("Stream done: " + msg.toString)))

  runnableGraph.onComplete {
    case Success(result) => logger.debug("Stream processed: {}", result)
    case Failure(e) => logger.warn("Failure in stream", e)
  }

//  Thread.sleep(3000) // Be sure the previous proposition is actually saved in ES.
  propositionService.update(propId,ZonedDateTime.now, "Il faut mettre a jour une proposition")
  logger.debug("Sent propositions...")
  //END EXPERIMENTAL

  val log = Logging(actorSystem.eventStream, "make-api")
  bindingFuture.map { serverBinding =>
    log.info(s"Shoppers API bound to ${serverBinding.localAddress} ")
  }.onComplete {
    case util.Failure(ex) =>
      log.error(ex, "Failed to bind to {}:{}!", host, port)
      actorSystem.terminate()
    case _ =>
  }

}

trait RequestTimeout {

  import scala.concurrent.duration._

  def requestTimeout(config: Config): Timeout = {
    val t = config.getString("akka.http.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }
}

class MakeSettings(config: Config) extends Extension {

  val passivateTimeout: Duration = Duration(config.getString("make-api.passivate-timeout"))
  val useEmbeddedElasticSearch: Boolean =
    if (config.hasPath("make-api.dev.embeddedElasticSearch")) config.getBoolean("make-api.dev.embeddedElasticSearch")
    else false

  object http {
    val host: String = config.getString("make-api.http.host")
    val port: Int = config.getInt("make-api.http.port")
  }

}



