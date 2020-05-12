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

import akka.Done
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.index.CreateIndexResponse
import com.sksamuel.elastic4s.http.index.admin.AliasActionResponse
import com.typesafe.scalalogging.StrictLogging
import eu.timepit.refined.auto._
import org.make.api.ActorSystemComponent
import org.make.api.idea._
import org.make.api.operation.PersistentOperationOfQuestionServiceComponent
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.job.JobCoordinatorServiceComponent
import org.make.api.technical.{ReadJournalComponent, TimeSettings}
import org.make.core.idea.Idea
import org.make.core.job.Job.JobId.Reindex
import org.make.core.operation.OperationOfQuestion
import org.make.core.proposal.ProposalId
import org.make.core.user.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait IndexationComponent {
  def indexationService: IndexationService
}

sealed trait EntitiesToIndex
case object IndexIdeas extends EntitiesToIndex
case object IndexOrganisations extends EntitiesToIndex
case object IndexProposals extends EntitiesToIndex
case object IndexOperationOfQuestions extends EntitiesToIndex

trait IndexationService {
  def reindexData(
    forceIdeas: Boolean,
    forceOrganisations: Boolean,
    forceProposals: Boolean,
    forceOperationOfQuestions: Boolean
  ): Future[JobAcceptance]
  def indicesToReindex(
    forceIdeas: Boolean,
    forceOrganisations: Boolean,
    forceProposals: Boolean,
    forceOperationOfQuestions: Boolean
  ): Future[Set[EntitiesToIndex]]
}

//TODO: test this component
trait DefaultIndexationComponent
    extends IndexationComponent
    with IdeaIndexationStream
    with OrganisationIndexationStream
    with ProposalIndexationStream
    with OperationOfQuestionIndexationStream {

  this: ElasticsearchConfigurationComponent
    with ElasticsearchClientComponent
    with StrictLogging
    with ActorSystemComponent
    with JobCoordinatorServiceComponent
    with ReadJournalComponent
    with PersistentOperationOfQuestionServiceComponent =>

  override lazy val indexationService: IndexationService = new DefaultIndexationService

  class DefaultIndexationService extends IndexationService {

    private lazy val client = elasticsearchClient.client

    private implicit val timeout: Timeout = TimeSettings.defaultTimeout

    private def reindexOrganisationsIfNeeded(needsReindex: Boolean): Future[Done] = {
      if (needsReindex) {
        logger.info("Reindexing organisations")
        val organisationIndexName =
          elasticsearchClient.createIndexName(elasticsearchConfiguration.organisationAliasName)
        for {
          _      <- executeCreateIndex(elasticsearchConfiguration.organisationAliasName, organisationIndexName)
          result <- executeIndexOrganisations(organisationIndexName)
          _      <- executeSetAlias(elasticsearchConfiguration.organisationAliasName, organisationIndexName)
        } yield result
      } else {
        Future.successful(Done)
      }
    }

    private def reindexIdeasIfNeeded(needsReindex: Boolean): Future[Done] = {
      if (needsReindex) {
        logger.info("Reindexing ideas")
        val ideaIndexName = elasticsearchClient.createIndexName(elasticsearchConfiguration.ideaAliasName)
        for {
          _           <- executeCreateIndex(elasticsearchConfiguration.ideaAliasName, ideaIndexName)
          resultIdeas <- executeIndexIdeas(ideaIndexName)
          _           <- executeSetAlias(elasticsearchConfiguration.ideaAliasName, ideaIndexName)
        } yield resultIdeas
      } else {
        Future.successful(Done)
      }
    }

    private def reindexProposalsIfNeeded(needsReindex: Boolean): Future[Done] = {
      if (needsReindex) {
        logger.info("Reindexing proposals")
        val proposalIndexName =
          elasticsearchClient.createIndexName(elasticsearchConfiguration.proposalAliasName)
        for {
          _               <- executeCreateIndex(elasticsearchConfiguration.proposalAliasName, proposalIndexName)
          resultProposals <- executeIndexProposal(proposalIndexName)
          _               <- executeSetAlias(elasticsearchConfiguration.proposalAliasName, proposalIndexName)
        } yield resultProposals
      } else {
        Future.successful(Done)
      }
    }

    private def reindexOperationOfQuestionsIfNeeded(needsReindex: Boolean): Future[Done] = {
      if (needsReindex) {
        logger.info("Reindexing operation of questions")
        val operationOfQuestionIndexName =
          elasticsearchClient.createIndexName(elasticsearchConfiguration.operationOfQuestionAliasName)
        for {
          _      <- executeCreateIndex(elasticsearchConfiguration.operationOfQuestionAliasName, operationOfQuestionIndexName)
          result <- executeIndexOperationOfQuestions(operationOfQuestionIndexName)
          _      <- executeSetAlias(elasticsearchConfiguration.operationOfQuestionAliasName, operationOfQuestionIndexName)
        } yield result
      } else {
        Future.successful(Done)
      }
    }

    override def reindexData(
      forceIdeas: Boolean,
      forceOrganisations: Boolean,
      forceProposals: Boolean,
      forceOperationOfQuestions: Boolean
    ): Future[JobAcceptance] =
      jobCoordinatorService.start(Reindex) { report =>
        logger.info(s"Elasticsearch Reindexation")
        indicesToReindex(forceIdeas, forceOrganisations, forceProposals, forceOperationOfQuestions).flatMap {
          indicesNotUpToDate =>
            // !mandatory: run organisation indexation before proposals and not in parallel
            reindexOrganisationsIfNeeded(indicesNotUpToDate.contains(IndexOrganisations))
              .map(_ => indicesNotUpToDate)
              .recoverWith {
                case e =>
                  logger.error("Organisations indexation failed", e)
                  Future.successful(indicesNotUpToDate)
              }
        }.flatMap { indicesNotUpToDate =>
          report(25d).map(_ => indicesNotUpToDate)
        }.flatMap { indicesNotUpToDate =>
          val futureIdeasIndexation: Future[Done] = reindexIdeasIfNeeded(indicesNotUpToDate.contains(IndexIdeas))
          val futureProposalsIndexation = reindexProposalsIfNeeded(indicesNotUpToDate.contains(IndexProposals))
          val futureOperationOfQuestionsIndexation =
            reindexOperationOfQuestionsIfNeeded(indicesNotUpToDate.contains(IndexOperationOfQuestions))

          for {
            _ <- futureIdeasIndexation
            _ <- futureProposalsIndexation
            _ <- futureOperationOfQuestionsIndexation
          } yield ()
        }
      }

    override def indicesToReindex(
      forceIdeas: Boolean,
      forceOrganisations: Boolean,
      forceProposals: Boolean,
      forceOperationOfQuestions: Boolean
    ): Future[Set[EntitiesToIndex]] = {
      val hashes: Map[EntitiesToIndex, String] = Map(
        IndexIdeas -> elasticsearchClient.hashForAlias(elasticsearchConfiguration.ideaAliasName),
        IndexOrganisations -> elasticsearchClient.hashForAlias(elasticsearchConfiguration.organisationAliasName),
        IndexProposals -> elasticsearchClient.hashForAlias(elasticsearchConfiguration.proposalAliasName),
        IndexOperationOfQuestions -> elasticsearchClient.hashForAlias(
          elasticsearchConfiguration.operationOfQuestionAliasName
        )
      )
      elasticsearchClient.getCurrentIndicesName.map { currentIndices =>
        val currentHashes: Seq[String] = currentIndices.map(elasticsearchClient.getHashFromIndex)
        var result: Set[EntitiesToIndex] = Set.empty
        if (forceIdeas) {
          result += IndexIdeas
        }
        if (forceOrganisations) {
          result += IndexOrganisations
        }
        if (forceProposals) {
          result += IndexProposals
        }
        if (forceOperationOfQuestions) {
          result += IndexOperationOfQuestions
        }
        result ++= hashes.flatMap {
          case (entitiesToIndex, hash) if !currentHashes.contains(hash) => Some(entitiesToIndex)
          case _                                                        => None
        }.toSet

        result
      }
    }

    private def addAndRemoveAlias(
      aliasName: String,
      newIndexName: String,
      indexes: Seq[String]
    ): Future[AliasActionResponse] = {
      if (indexes.isEmpty) {
        logger.error("indexes with alias is empty")
      }

      client.executeAsFuture {
        aliases(addAlias(aliasName).on(newIndexName), indexes.map { index =>
          removeAlias(aliasName).on(index)
        }: _*)
      }
    }

    private def executeSetAlias(aliasName: String, indexName: String): Future[Done] = {

      logger.info(s"calling executeSetAlias for $aliasName - $indexName")

      client.executeAsFuture {
        getAliases(Seq.empty, Seq(aliasName))
      }.flatMap { getAliasResponse =>
        addAndRemoveAlias(aliasName, indexName, getAliasResponse.mappings.keys.map(_.name).toSeq).map(_ => Done)
      }.recoverWith {
        case e =>
          logger.error("fail to retrieve ES alias", e)
          Future.successful(Done)
      }
    }

    private def executeCreateIndex(aliasName: String, indexName: String): Future[CreateIndexResponse] = {
      client.executeAsFuture(createIndex(indexName).source(elasticsearchClient.mappingForAlias(aliasName)))
    }

    private def executeIndexProposal(proposalIndexName: String)(implicit mat: Materializer): Future[Done] = {
      val start = System.currentTimeMillis()

      val result =
        proposalJournal
          .currentPersistenceIds()
          .map(ProposalId.apply)
          .via(ProposalStream.flowIndexProposals(proposalIndexName))
          .runWith(Sink.ignore)

      result.onComplete {
        case Success(_) =>
          logger.info("Proposal indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) =>
          logger.error(s"Proposal indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }

    private def executeIndexIdeas(indexName: String)(implicit mat: Materializer): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = persistentIdeaService
        .findAll(IdeaFiltersRequest.empty)
        .flatMap { ideas =>
          logger.info(s"Ideas to index: ${ideas.size}")
          Source[Idea](ideas)
            .via(IdeaStream.flowIndexIdeas(indexName))
            .runWith(Sink.ignore)
        }

      result.onComplete {
        case Success(_) => logger.info("Idea indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) => logger.error(s"Idea indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }

    private def executeIndexOrganisations(indexName: String)(implicit mat: Materializer): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = persistentUserService
        .findAllOrganisations()
        .flatMap { organisations =>
          logger.info(s"Organisations to index: ${organisations.size}")
          Source[User](organisations)
            .via(OrganisationStream.flowIndexOrganisations(indexName)(mat))
            .runWith(Sink.ignore)
        }

      result.onComplete {
        case Success(_) => logger.info("Organisation indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) =>
          logger.error(s"Organisation indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }

    private def executeIndexOperationOfQuestions(indexName: String)(implicit mat: Materializer): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = persistentOperationOfQuestionService
        .find()
        .flatMap { operationOfQuestions =>
          logger.info(s"Operation of questions to index: ${operationOfQuestions.size}")
          Source[OperationOfQuestion](operationOfQuestions)
            .via(OperationOfQuestionStream.flowIndexOrganisations(indexName))
            .runWith(Sink.ignore)
        }

      result.onComplete {
        case Success(_) =>
          logger.info("Operation of questions indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) =>
          logger.error(s"Operation of questions indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }
  }
}
