/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.technical.elasticsearch

import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.ElasticDsl.{aliases, _}
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.index.CreateIndexResponse
import com.sksamuel.elastic4s.http.index.admin.AliasActionResponse
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.idea._
import org.make.api.technical.ReadJournalComponent
import org.make.core.idea.Idea

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait IndexationComponent {
  def indexationService: IndexationService
}

sealed trait EntitiesToIndex
case object IndexIdeas extends EntitiesToIndex
case object IndexProposals extends EntitiesToIndex
case object IndexSequences extends EntitiesToIndex

trait IndexationService {
  def reindexData(forceIdeas: Boolean, forceProposals: Boolean, forceSequences: Boolean): Future[Done]
  def indicesToReindex(forceIdeas: Boolean,
                       forceProposals: Boolean,
                       forceSequences: Boolean): Future[Set[EntitiesToIndex]]
}

//TODO: test this component
trait DefaultIndexationComponent
    extends IndexationComponent
    with ProposalIndexationStream
    with SequenceIndexationStream
    with IdeaIndexationStream {
  this: ElasticsearchConfigurationComponent with StrictLogging with ActorSystemComponent with ReadJournalComponent =>

  override lazy val indexationService: IndexationService = new IndexationService {

    implicit private val mat: ActorMaterializer = ActorMaterializer()(actorSystem)
    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )

    override def reindexData(forceIdeas: Boolean = false,
                             forceProposals: Boolean = false,
                             forceSequences: Boolean = false): Future[Done] = {
      logger.info(s"Elasticsearch Reindexation")
      indicesToReindex(forceIdeas, forceProposals, forceSequences).map { indicesNotUpToDate =>
        if (indicesNotUpToDate.contains(IndexIdeas)) {
          logger.info("Reindexing ideas")
          val ideaIndexName = elasticsearchConfiguration.createIndexName(elasticsearchConfiguration.ideaAliasName)
          for {
            _           <- executeCreateIndex(elasticsearchConfiguration.ideaAliasName, ideaIndexName)
            resultIdeas <- executeIndexIdeas(ideaIndexName)
            _           <- executeSetAlias(elasticsearchConfiguration.ideaAliasName, ideaIndexName)
          } yield resultIdeas
        }
        if (indicesNotUpToDate.contains(IndexProposals) || indicesNotUpToDate.contains(IndexSequences)) {
          reindexProposalsAndSequences(
            indicesNotUpToDate.contains(IndexProposals),
            indicesNotUpToDate.contains(IndexSequences),
            elasticsearchConfiguration.createIndexName(elasticsearchConfiguration.proposalAliasName),
            elasticsearchConfiguration.createIndexName(elasticsearchConfiguration.sequenceAliasName)
          )
        }
      }.flatMap { _ =>
        Future.successful(Done)
      }
    }

    override def indicesToReindex(forceIdeas: Boolean,
                                  forceProposals: Boolean,
                                  forceSequences: Boolean): Future[Set[EntitiesToIndex]] = {
      val currentHashes: Map[EntitiesToIndex, String] = Map(
        IndexIdeas -> elasticsearchConfiguration.hashForAlias(elasticsearchConfiguration.ideaAliasName),
        IndexProposals -> elasticsearchConfiguration.hashForAlias(elasticsearchConfiguration.proposalAliasName),
        IndexSequences -> elasticsearchConfiguration.hashForAlias(elasticsearchConfiguration.sequenceAliasName)
      )
      elasticsearchConfiguration.getCurrentIndicesName.map { currentIndices =>
        var result: Set[EntitiesToIndex] = Set.empty
        if (forceIdeas) {
          result += IndexIdeas
        }
        if (forceProposals) {
          result += IndexProposals
        }
        if (forceSequences) {
          result += IndexSequences
        }
        result ++= currentHashes.flatMap {
          case (entitiesToIndex, hash) if !currentIndices.contains(hash) => Some(entitiesToIndex)
          case _                                                         => None
        }.toSet
        result
      }
    }

    private def addAndRemoveAlias(aliasName: String,
                                  newIndexName: String,
                                  indexes: Seq[String]): Future[AliasActionResponse] = {
      if (indexes.isEmpty) {
        logger.error("indexes with alias is empty")
      }

      elasticsearchConfiguration.client.executeAsFuture {
        aliases(addAlias(aliasName).on(newIndexName), indexes.map { index =>
          removeAlias(aliasName).on(index)
        }: _*)
      }
    }

    private def executeSetAlias(aliasName: String, indexName: String): Future[Done] = {
      client.executeAsFuture {
        getAliases(Seq.empty, Seq(aliasName))
      }.onComplete {
        case Success(getAliasResponse) =>
          addAndRemoveAlias(aliasName, indexName, getAliasResponse.mappings.keys.map(_.name).toSeq)
        case Failure(e) => logger.error("fail to retrieve ES alias", e)
        case _          => logger.error("fail to retrieve ES alias")
      }

      Future.successful(Done)
    }

    private def executeCreateIndex(aliasName: String, indexName: String): Future[CreateIndexResponse] = {
      elasticsearchConfiguration.client.executeAsFuture(
        createIndex(indexName).source(elasticsearchConfiguration.mappingForAlias(aliasName))
      )
    }

    def reindexProposalsAndSequences(indexProposals: Boolean,
                                     indexSequences: Boolean,
                                     proposalIndexName: String,
                                     sequenceIndexName: String): Future[Done] = {
      def futureCreateIndices: Future[Unit] =
        (indexProposals, indexSequences) match {
          case (true, true) =>
            val createSequenceIndex =
              executeCreateIndex(elasticsearchConfiguration.sequenceAliasName, sequenceIndexName)
            executeCreateIndex(elasticsearchConfiguration.proposalAliasName, proposalIndexName)
              .flatMap(_ => createSequenceIndex)
              .map(_ => {})
          case (true, _) =>
            executeCreateIndex(elasticsearchConfiguration.proposalAliasName, proposalIndexName).map(_ => {})
          case (_, true) =>
            executeCreateIndex(elasticsearchConfiguration.sequenceAliasName, sequenceIndexName).map(_ => {})
          case _ => Future.successful({})
        }

      def futureSetAliases: Future[Unit] =
        (indexProposals, indexSequences) match {
          case (true, true) =>
            val setSequenceAlias = executeSetAlias(elasticsearchConfiguration.sequenceAliasName, sequenceIndexName)
            executeSetAlias(elasticsearchConfiguration.proposalAliasName, proposalIndexName)
              .flatMap(_ => setSequenceAlias)
              .map(_ => {})
          case (true, _) =>
            executeSetAlias(elasticsearchConfiguration.proposalAliasName, proposalIndexName).map(_ => {})
          case (_, true) =>
            executeSetAlias(elasticsearchConfiguration.sequenceAliasName, sequenceIndexName).map(_ => {})
          case _ => Future.successful({})
        }

      for {
        _ <- futureCreateIndices
        _ <- executeCreateIndex(elasticsearchConfiguration.proposalAliasName, proposalIndexName)
        resultIndexation <- executeIndexProposalsAndSequences(
          indexProposals,
          indexSequences,
          proposalIndexName,
          sequenceIndexName
        )
        _ <- futureSetAliases
      } yield resultIndexation
    }

    private def executeIndexProposalsAndSequences(
      indexProposals: Boolean,
      indexSequences: Boolean,
      proposalIndexName: String,
      sequenceIndexName: String
    )(implicit mat: Materializer): Future[Done] = {
      val start = System.currentTimeMillis()

      val source: Source[String, NotUsed] = readJournal.currentPersistenceIds()
      val sink = Sink.ignore

      val indexationFlow: Flow[String, Done, NotUsed] = Flow.fromGraph[String, Done, NotUsed](GraphDSL.create() {
        implicit builder: GraphDSL.Builder[NotUsed] =>
          val bcast = builder.add(Broadcast[String](2))
          val merge = builder.add(Merge[Done](2))

          val filterExecuteProposals: Flow[String, String, NotUsed] = Flow[String].filter(_ => indexProposals)
          val filterExecuteSequences: Flow[String, String, NotUsed] = Flow[String].filter(_ => indexSequences)

          bcast.out(0) ~> filterExecuteProposals ~> ProposalStream.flowIndexProposals(proposalIndexName) ~> merge
          bcast.out(1) ~> filterExecuteSequences ~> SequenceStream.flowIndexSequences(sequenceIndexName) ~> merge

          FlowShape(bcast.in, merge.out)
      })

      val result: Future[Done] = source.via(indexationFlow).runWith(sink)

      result.onComplete {
        case Success(_) =>
          logger.info("proposal and/or sequence indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) =>
          logger.error(s"proposal and/or sequence indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }

    private def executeIndexIdeas(indexName: String)(implicit mat: Materializer): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = persistentIdeaService
        .findAll(IdeaFiltersRequest.empty)
        .flatMap { ideas =>
          logger.info(s"Ideas to index: ${ideas.size}")
          Source[Idea](immutable.Seq(ideas: _*))
            .via(IdeaStream.flowIndexIdeas(indexName))
            .runWith(Sink.ignore)
        }

      result.onComplete {
        case Success(_) => logger.info("Idea indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) => logger.error(s"Idea indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      Future.successful(Done)
    }
  }
}
