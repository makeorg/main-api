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

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.elastic4s.http.ElasticDsl.{aliases, _}
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.index.CreateIndexResponse
import com.sksamuel.elastic4s.http.index.admin.AliasActionResponse
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.idea._
import org.make.api.proposal.{
  ProposalCoordinatorServiceComponent,
  ProposalScorerHelper,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.semantic.SemanticComponent
import org.make.api.sequence.{SequenceCoordinatorServiceComponent, SequenceSearchEngine, SequenceSearchEngineComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.tagtype.PersistentTagTypeServiceComponent
import org.make.api.technical.ReadJournalComponent
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.idea.Idea
import org.make.core.idea.indexed.IndexedIdea
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.{
  Author,
  IndexedOrganisationInfo,
  IndexedProposal,
  IndexedScores,
  IndexedVote,
  Context => ProposalContext
}
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.sequence.SequenceId
import org.make.core.sequence.indexed.{
  IndexedSequence,
  IndexedSequenceProposalId,
  IndexedSequenceTheme,
  Context => SequenceContext
}

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble
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
  def indicesNotUpToDate(forceIdeas: Boolean,
                         forceProposals: Boolean,
                         forceSequences: Boolean): Future[Set[EntitiesToIndex]]
}

//TODO: test this component
trait DefaultIndexationComponent extends IndexationComponent {
  this: ElasticsearchConfigurationComponent
    with StrictLogging
    with ActorSystemComponent
    with ProposalCoordinatorServiceComponent
    with SequenceCoordinatorServiceComponent
    with ReadJournalComponent
    with UserServiceComponent
    with TagServiceComponent
    with ThemeServiceComponent
    with ProposalSearchEngineComponent
    with ProposalSearchEngineComponent
    with SequenceSearchEngineComponent
    with IdeaSearchEngineComponent
    with PersistentIdeaServiceComponent
    with SemanticComponent
    with PersistentTagTypeServiceComponent =>

  override lazy val indexationService: IndexationService = new IndexationService {

    implicit private val mat: ActorMaterializer = ActorMaterializer()(actorSystem)
    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )

    override def reindexData(forceIdeas: Boolean = false,
                             forceProposals: Boolean = false,
                             forceSequences: Boolean = false): Future[Done] = {
      logger.info(s"Elasticsearch Reindexation")
      indicesNotUpToDate(forceIdeas, forceProposals, forceSequences).map { indicesNotUpToDate =>
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

    override def indicesNotUpToDate(forceIdeas: Boolean,
                                    forceProposals: Boolean,
                                    forceSequences: Boolean): Future[Set[EntitiesToIndex]] = {
      if (forceIdeas && forceProposals && forceSequences) {
        Future.successful(Set(IndexIdeas, IndexProposals, IndexSequences))
      } else {
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
      val parallelism = 5

      val source: Source[String, NotUsed] = readJournal.currentPersistenceIds()
      val sink = Sink.ignore

      val indexationFlow: Flow[String, Done, NotUsed] = Flow.fromGraph[String, Done, NotUsed](GraphDSL.create() {
        implicit builder: GraphDSL.Builder[NotUsed] =>
          val bcast = builder.add(Broadcast[String](2))
          val merge = builder.add(Merge[Done](2))

          val maybeIndexedProposal: Flow[String, Option[IndexedProposal], NotUsed] =
            Flow[String].mapAsync(parallelism)(persistenceId => getIndexedProposal(ProposalId(persistenceId)))

          val maybeIndexedSequence: Flow[String, Option[IndexedSequence], NotUsed] =
            Flow[String].mapAsync(parallelism)(persistenceId => getIndexedSequence(SequenceId(persistenceId)))

          val runIndexProposals: Flow[Seq[IndexedProposal], Done, NotUsed] =
            Flow[Seq[IndexedProposal]]
              .mapAsync(parallelism)(proposals => executeIndexProposals(proposals, proposalIndexName))

          val runIndexSequences: Flow[Seq[IndexedSequence], Done, NotUsed] =
            Flow[Seq[IndexedSequence]]
              .mapAsync(parallelism)(sequences => executeIndexSequences(sequences, sequenceIndexName))

          def filterIsDefined[T]: Flow[Option[T], T, NotUsed] = Flow[Option[T]].filter(_.isDefined).map(_.get)

          def grouped[T]: Flow[T, Seq[T], NotUsed] = Flow[T].groupedWithin(100, 500.milliseconds)

          val filterExecuteProposals: Flow[String, String, NotUsed] = Flow[String].filter(_ => indexProposals)
          val filterExecuteSequences: Flow[String, String, NotUsed] = Flow[String].filter(_ => indexSequences)

          bcast.out(0)               ~> filterExecuteProposals ~> maybeIndexedProposal ~> filterIsDefined[IndexedProposal] ~>
            grouped[IndexedProposal] ~> runIndexProposals      ~> merge
          bcast.out(1)               ~> filterExecuteSequences ~> maybeIndexedSequence ~> filterIsDefined[IndexedSequence] ~>
            grouped[IndexedSequence] ~> runIndexSequences      ~> merge

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
  }

  private def executeIndexProposals(proposals: Seq[IndexedProposal], indexName: String): Future[Done] =
    elasticsearchProposalAPI
      .indexProposals(proposals, Some(IndexAndType(indexName, ProposalSearchEngine.proposalIndexName)))
      .recoverWith {
        case e =>
          logger.error(s"Indexing proposals failed", e)
          Future.successful(Done)
      }
      .flatMap(_ => Future.traverse(proposals)(semanticService.indexProposal).map(_ => Done))
      .recoverWith {
        case e =>
          logger.error("indexaliasNameing a proposal in semantic failed", e)
          Future.successful(Done)
      }

  private def executeIndexSequences(sequences: Seq[IndexedSequence], indexName: String): Future[Done] =
    elasticsearchSequenceAPI
      .indexSequences(sequences, Some(IndexAndType(indexName, SequenceSearchEngine.sequenceIndexName)))
      .recoverWith {
        case e =>
          logger.error("Indexing sequences failed", e)
          Future.successful(Done)
      }

  private def executeIndexIdeas(indexName: String)(implicit mat: Materializer): Future[Done] = {
    val start = System.currentTimeMillis()

    val result = persistentIdeaService
      .findAll(IdeaFiltersRequest.empty)
      .flatMap { ideas =>
        logger.info(s"Ideas to index: ${ideas.size}")
        Source[Idea](immutable.Seq(ideas: _*))
          .groupedWithin(100, 500.milliseconds)
          .mapAsync(3) { ideas =>
            elasticsearchIdeaAPI
              .indexIdeas(
                ideas.map(IndexedIdea.createFromIdea),
                Some(IndexAndType(indexName, IdeaSearchEngine.ideaIndexName))
              )
              .recoverWith {
                case e =>
                  logger.error(s"Indexing ideas failed", e)
                  Future.successful(Done)
              }
          }
          .runWith(Sink.ignore)
      }

    result.onComplete {
      case Success(_) => logger.info("Idea indexation success in {} ms", System.currentTimeMillis() - start)
      case Failure(e) => logger.error(s"Idea indexation failed in ${System.currentTimeMillis() - start} ms", e)
    }

    Future.successful(Done)
  }

  private def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
    themeService
      .findAll()
      .map(_.filter(theme => themeIds.contains(theme.themeId)))
      .map(Some(_))
  }

  private def getIndexedProposal(proposalId: ProposalId): Future[Option[IndexedProposal]] = {
    val maybeResult: OptionT[Future, IndexedProposal] = for {
      proposal <- OptionT(proposalCoordinatorService.getProposal(proposalId))
      user     <- OptionT(userService.getUser(proposal.author))
      tags     <- OptionT(tagService.retrieveIndexedTags(proposal.tags))
    } yield {
      IndexedProposal(
        id = proposal.proposalId,
        userId = proposal.author,
        content = proposal.content,
        slug = proposal.slug,
        status = proposal.status,
        createdAt = proposal.createdAt.get,
        updatedAt = proposal.updatedAt,
        votes = proposal.votes.map(IndexedVote.apply),
        scores = IndexedScores(
          engagement = ProposalScorerHelper.engagement(proposal),
          adhesion = ProposalScorerHelper.adhesion(proposal),
          realistic = ProposalScorerHelper.realistic(proposal),
          topScore = ProposalScorerHelper.topScore(proposal),
          controversy = ProposalScorerHelper.controversy(proposal),
          rejection = ProposalScorerHelper.rejection(proposal)
        ),
        context = Some(
          ProposalContext(
            operation = proposal.creationContext.operationId,
            source = proposal.creationContext.source,
            location = proposal.creationContext.location,
            question = proposal.creationContext.question
          )
        ),
        trending = None,
        labels = proposal.labels.map(_.value),
        author = Author(
          firstName = user.firstName,
          organisationName = user.organisationName,
          postalCode = user.profile.flatMap(_.postalCode),
          age = user.profile
            .flatMap(_.dateOfBirth)
            .map(date => ChronoUnit.YEARS.between(date, LocalDate.now()).toInt),
          avatarUrl = user.profile.flatMap(_.avatarUrl)
        ),
        organisations = proposal.organisations.map(IndexedOrganisationInfo.apply),
        country = proposal.country.getOrElse("FR"),
        language = proposal.language.getOrElse("fr"),
        themeId = proposal.theme,
        tags = tags,
        ideaId = proposal.idea,
        operationId = proposal.operation
      )
    }

    maybeResult.value
  }

  private def getIndexedSequence(sequenceId: SequenceId): Future[Option[IndexedSequence]] = {
    val maybeResult: OptionT[Future, IndexedSequence] = for {
      sequence <- OptionT(sequenceCoordinatorService.getSequence(sequenceId))
      themes   <- OptionT(retrieveThemes(sequence.themeIds))
    } yield {
      IndexedSequence(
        id = sequence.sequenceId,
        title = sequence.title,
        slug = sequence.slug,
        translation = sequence.sequenceTranslation,
        status = sequence.status,
        createdAt = sequence.createdAt.get,
        updatedAt = sequence.updatedAt.get,
        context = Some(
          SequenceContext(
            operation = sequence.creationContext.operationId,
            source = sequence.creationContext.source,
            location = sequence.creationContext.location,
            question = sequence.creationContext.question
          )
        ),
        themes = themes.map(theme => IndexedSequenceTheme(themeId = theme.themeId, translation = theme.translations)),
        operationId = sequence.operationId,
        proposals = sequence.proposalIds.map(IndexedSequenceProposalId.apply),
        searchable = sequence.searchable
      )
    }

    maybeResult.value
  }
}
