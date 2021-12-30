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
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.stream.scaladsl._
import akka.util.Timeout
import cats.data.NonEmptyList
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.CreateIndexResponse
import com.sksamuel.elastic4s.requests.indexes.admin.AliasActionResponse
import eu.timepit.refined.auto._
import grizzled.slf4j.Logging
import org.make.api.idea._
import org.make.api.operation.PersistentOperationOfQuestionServiceComponent
import org.make.api.post.PostServiceComponent
import org.make.api.technical.Futures._
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.job.JobCoordinatorServiceComponent
import org.make.api.technical.{ActorSystemComponent, ReadJournalComponent, TimeSettings}
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.idea.Idea
import org.make.core.job.Job.JobId.{Reindex, ReindexPosts}
import org.make.core.operation.indexed.OperationOfQuestionSearchResult
import org.make.core.operation.{
  OperationKind,
  OperationKindsSearchFilter,
  OperationOfQuestion,
  OperationOfQuestionSearchFilters,
  OperationOfQuestionSearchQuery,
  StatusSearchFilter
}
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
case object IndexPost extends EntitiesToIndex

trait IndexationService {
  def reindexData(
    forceIdeas: Boolean,
    forceOrganisations: Boolean,
    forceProposals: Boolean,
    forceOperationOfQuestions: Boolean
  ): Future[JobAcceptance]
  def reindexPostsData(): Future[JobAcceptance]
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
    with OperationOfQuestionIndexationStream
    with PostIndexationStream {

  this: ActorSystemComponent
    with ElasticsearchConfigurationComponent
    with ElasticsearchClientComponent
    with Logging
    with JobCoordinatorServiceComponent
    with ReadJournalComponent
    with PersistentIdeaServiceComponent
    with PersistentOperationOfQuestionServiceComponent
    with PostServiceComponent =>

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
            // !mandatory: run question indexation before organisation and not in parallel
            reindexOperationOfQuestionsIfNeeded(indicesNotUpToDate.contains(IndexOperationOfQuestions))
              .map(_ => indicesNotUpToDate)
              .recoverWith {
                case e =>
                  logger.error("Questions indexation failed", e)
                  Future.successful(indicesNotUpToDate)
              }
        }.flatMap { indicesNotUpToDate =>
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

          for {
            _ <- futureIdeasIndexation
            _ <- futureProposalsIndexation
          } yield indicesNotUpToDate
        }.flatMap { toUpdate =>
          postReindex(toUpdate.contains(IndexProposals) || toUpdate.contains(IndexOperationOfQuestions))
        }
      }

    def postReindex(updateQuestion: Boolean): Future[Unit] = {
      if (updateQuestion) {
        executeUpdateOperationOfQuestions().toUnit
      } else {
        Future.unit
      }
    }

    override def reindexPostsData(): Future[JobAcceptance] =
      jobCoordinatorService.start(ReindexPosts) { report =>
        logger.info(s"Elasticsearch Posts Reindexation")
        val postIndexName = elasticsearchClient.createIndexName(elasticsearchConfiguration.postAliasName)
        for {
          _ <- executeCreateIndex(elasticsearchConfiguration.postAliasName, postIndexName)
          _ <- report(33d)
          _ <- executeIndexPosts(postIndexName)
          _ <- report(66d)
          _ <- executeSetAlias(elasticsearchConfiguration.postAliasName, postIndexName)
        } yield {}
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
        aliases(addAlias(aliasName, newIndexName), indexes.map(removeAlias(aliasName, _)): _*)
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

    private def executeIndexProposal(proposalIndexName: String): Future[Done] = {
      val start = System.currentTimeMillis()

      val result =
        proposalJournal
          .currentPersistenceIds()
          .map(ProposalId.apply)
          .via(ProposalStream.flowIndexProposals(proposalIndexName))
          .runWith(Sink.ignore)

      result.onComplete {
        case Success(_) =>
          val time = System.currentTimeMillis() - start
          logger.info(s"Proposal indexation success in $time ms")
        case Failure(e) =>
          val time = System.currentTimeMillis() - start
          logger.error(s"Proposal indexation failed in $time ms", e)
      }

      result
    }

    private def executeIndexIdeas(indexName: String): Future[Done] = {
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
        case Success(_) =>
          val time = System.currentTimeMillis() - start
          logger.info(s"Idea indexation success in $time ms")
        case Failure(e) =>
          val time = System.currentTimeMillis() - start
          logger.error(s"Idea indexation failed in $time ms", e)
      }

      result
    }

    private def executeIndexOrganisations(indexName: String): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = persistentUserService
        .findAllOrganisations()
        .flatMap { organisations =>
          logger.info(s"Organisations to index: ${organisations.size}")
          Source[User](organisations)
            .via(OrganisationStream.flowIndexOrganisations(indexName))
            .runWith(Sink.ignore)
        }

      result.onComplete {
        case Success(_) =>
          val time = System.currentTimeMillis() - start
          logger.info(s"Organisation indexation success in $time ms")
        case Failure(e) =>
          val time = System.currentTimeMillis() - start
          logger.error(s"Organisation indexation failed in $time ms", e)
      }

      result
    }

    private def executeIndexOperationOfQuestions(indexName: String): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = persistentOperationOfQuestionService
        .find()
        .flatMap { operationOfQuestions =>
          logger.info(s"Operation of questions to index: ${operationOfQuestions.size}")
          Source[OperationOfQuestion](operationOfQuestions)
            .via(OperationOfQuestionStream.flowIndexOperationOfQuestions(indexName))
            .runWith(Sink.ignore)
        }

      result.onComplete {
        case Success(_) =>
          val time = System.currentTimeMillis() - start
          logger.info(s"Operation of questions indexation success in $time ms")
        case Failure(e) =>
          val time = System.currentTimeMillis() - start
          logger.error(s"Operation of questions indexation failed in $time ms", e)
      }

      result
    }

    private def executeUpdateOperationOfQuestions(): Future[IndexationStatus] = {
      val start = System.currentTimeMillis()

      def query(limit: Int) = OperationOfQuestionSearchQuery(
        filters = Some(
          OperationOfQuestionSearchFilters(
            status = Some(StatusSearchFilter(OperationOfQuestion.Status.Open)),
            operationKinds = Some(OperationKindsSearchFilter(OperationKind.values))
          )
        ),
        limit = Some(limit)
      )

      @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
      def countProposals(retries: Int = 3): Future[Int] = {
        elasticsearchOperationOfQuestionAPI.count(OperationOfQuestionSearchQuery()).flatMap {
          case 0 if retries > 0 =>
            // Needed to wait for previous bulk indexation to actually persist in ES
            Thread.sleep(1000)
            countProposals(retries - 1)
          case 0 =>
            logger.warn("retrying but no retries left")
            Future.failed(
              new IllegalStateException(
                s"ES index ${elasticsearchConfiguration.operationOfQuestionAliasName} does not yet have indexed values."
              )
            )
          case _ =>
            elasticsearchOperationOfQuestionAPI.count(query(0)).map(_.toInt)
        }
      }

      def updateQuestions(result: OperationOfQuestionSearchResult): Future[IndexationStatus] = {
        logger.info(s"Operation of questions to update: ${result.results.size}")
        result.results.map(_.questionId) match {
          case Seq() => Future.successful(IndexationStatus.Completed)
          case head +: tail =>
            elasticsearchProposalAPI.computeTop20ConsensusThreshold(NonEmptyList(head, tail.toList)).flatMap {
              thresholds =>
                val questions = result.results.map { question =>
                  question.copy(top20ConsensusThreshold = thresholds.get(question.questionId))
                }
                elasticsearchOperationOfQuestionAPI.indexOperationOfQuestions(questions, None)
            }
        }
      }

      val result = for {
        count  <- countProposals()
        result <- elasticsearchOperationOfQuestionAPI.searchOperationOfQuestions(query(count))
        status <- updateQuestions(result)
      } yield status

      result.onComplete {
        case Success(IndexationStatus.Completed) =>
          logger.info(s"Operation of questions post reindex update success in ${System.currentTimeMillis() - start} ms")
        case Success(IndexationStatus.Failed(e)) =>
          logger.error(
            s"Operation of questions post reindex update failed in ${System.currentTimeMillis() - start} ms",
            e
          )
        case Failure(e) =>
          logger.error(
            s"Operation of questions post reindex update failed in ${System.currentTimeMillis() - start} ms",
            e
          )
      }

      result
    }

    private def executeIndexPosts(indexName: String): Future[Done] = {
      val start = System.currentTimeMillis()

      val result = Source
        .future(postService.fetchPostsForHome())
        .wireTap { posts =>
          logger.info(s"Posts to index: ${posts.size}")
        }
        .mapConcat(identity)
        .via(PostStream.flowIndexPosts(indexName))
        .runWith(Sink.ignore)

      result.onComplete {
        case Success(_) =>
          val time = System.currentTimeMillis() - start
          logger.info(s"Posts indexation success in $time ms")
        case Failure(e) =>
          val time = System.currentTimeMillis() - start
          logger.error(s"Posts indexation failed in $time ms", e)
      }

      result
    }
  }
}
