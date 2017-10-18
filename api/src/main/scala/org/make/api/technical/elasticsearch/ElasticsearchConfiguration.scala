package org.make.api.technical.elasticsearch

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.HttpClient
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.ConfigurationSupport
import com.sksamuel.elastic4s.http.ElasticDsl._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{Failure, Success}

class ElasticsearchConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport
    with StrictLogging {

  val connectionString: String = configuration.getString("connection-string")
  val indexName: String = configuration.getString("index-name")
  // create index
  val elasticsearchMapping: String = Source.fromResource("elasticsearch-mapping.json").getLines().mkString("")

  private val client = HttpClient(ElasticsearchClientUri(s"elasticsearch://$connectionString"))

  client.execute {
    createIndex(indexName).source(elasticsearchMapping)
  }.onComplete {
    case Success(_) =>
      logger.info(s"Elasticsearch index $indexName created")
    case Failure(e) =>
      logger.error(s"Error when creating Elasticsearch index $indexName", e)
  }
}

object ElasticsearchConfiguration extends ExtensionId[ElasticsearchConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticsearchConfiguration =
    new ElasticsearchConfiguration(system.settings.config.getConfig("make-api.elasticSearch"))

  override def lookup(): ExtensionId[ElasticsearchConfiguration] =
    ElasticsearchConfiguration
  override def get(system: ActorSystem): ElasticsearchConfiguration =
    super.get(system)
}

trait ElasticsearchConfigurationExtension extends ElasticsearchConfigurationComponent { this: Actor =>
  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    ElasticsearchConfiguration(context.system)
}

trait ElasticsearchConfigurationComponent {
  def elasticsearchConfiguration: ElasticsearchConfiguration
}
