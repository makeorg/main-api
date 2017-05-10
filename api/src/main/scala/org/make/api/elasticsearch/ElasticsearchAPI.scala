package org.make.api.elasticsearch

import java.time.ZonedDateTime
import java.util.UUID

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.core.proposition.PropositionId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CustomFormatters {
  implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] = Decoder.decodeString.map(ZonedDateTime.parse)
  implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] = (a: ZonedDateTime) => Json.fromString(a.toString)

  implicit val uuidDecoder: Decoder[UUID] = Decoder.decodeString.map(UUID.fromString)
  implicit val uuidEncoder: Encoder[UUID] = (a: UUID) => Json.fromString(a.toString)
}


class ElasticsearchAPI extends CustomFormatters with StrictLogging {
  val client = HttpClient(ElasticsearchClientUri("localhost", 9200))

  val propositionIndex: IndexAndType = "propositions" / "proposition"
  def getPropositionById(propositionId: PropositionId): Future[Option[PropositionElasticsearch]] = {
    client execute {
      get(id = propositionId.value).from(propositionIndex)
    } flatMap {
      PropositionElasticsearch.shape
        .applyOrElse[AnyRef, Future[Option[PropositionElasticsearch]]](_, _ => Future.successful(None))
    }
  }

  def save(record: PropositionElasticsearch): Future[Done] = {
    logger.info(s"Saving in Elasticsearch: $record")
    client.execute {
      indexInto(propositionIndex) doc record refresh RefreshPolicy.IMMEDIATE id record.id.toString
    } map { _ => Done }
  }

  def updateProposition(record: PropositionElasticsearch): Future[Done] = {
    logger.info(s"Saving in Elasticsearch: $record")
    client.execute {
      update(id = record.id.toString) in propositionIndex doc record refresh RefreshPolicy.IMMEDIATE
    } map { _ => Done }
  }
}

object ElasticsearchAPI {
  val api = new ElasticsearchAPI
}