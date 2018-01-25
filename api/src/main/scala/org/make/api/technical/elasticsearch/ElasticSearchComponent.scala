package org.make.api.technical.elasticsearch

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import akka.Done
import akka.stream.ActorMaterializer
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.elastic4s.http.ElasticDsl.{aliases, _}
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.index.CreateIndexResponse
import com.sksamuel.elastic4s.http.index.admin.IndicesAliasResponse
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.idea._
import org.make.api.proposal.{ProposalCoordinatorServiceComponent, ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.sequence.{SequenceCoordinatorServiceComponent, SequenceSearchEngine, SequenceSearchEngineComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.ReadJournalComponent
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.DateHelper
import org.make.core.idea.indexed.IndexedIdea
import org.make.core.proposal.indexed.{Author, IndexedProposal, IndexedVote, Context => ProposalContext}
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.reference.{Tag, TagId, Theme, ThemeId}
import org.make.core.sequence.indexed.{IndexedSequence, IndexedSequenceProposalId, IndexedSequenceTheme, Context => SequenceContext}
import org.make.core.sequence.{Sequence, SequenceId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ElasticSearchComponent {
  def elasticSearch: ElasticSearch
}

trait ElasticSearch {
  def reindexData(): Future[Done]
}

trait DefaultElasticSearchComponent extends ElasticSearchComponent {
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
    with DefaultPersistentIdeaServiceComponent =>

  override lazy val elasticSearch: ElasticSearch = new ElasticSearch {

    implicit private val mat: ActorMaterializer = ActorMaterializer()(actorSystem)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )

    override def reindexData(): Future[Done] = {
      logger.info("Elasticsearch Reindexation Begin")

      val newIndexName = elasticsearchConfiguration.indexName + "-" + dateFormatter.format(DateHelper.now())

      for {
        _      <- executeCreateIndex(newIndexName)
        _      <- executeIndexProposals(newIndexName)
        _      <- executeIndexSequences(newIndexName)
        _      <- executeIndexIdeas(newIndexName)
        result <- executeSetAlias(newIndexName)
      } yield result
    }

    private def addAndRemoveAlias(newIndexName: String, indexes: Seq[String]): Future[IndicesAliasResponse] = {

      if (indexes.isEmpty) {
        logger.error("indexes with alias is empty")
      }

      elasticsearchConfiguration.client.execute {
        aliases(addAlias(elasticsearchConfiguration.aliasName).on(newIndexName), indexes.map { index =>
          removeAlias(elasticsearchConfiguration.aliasName).on(index)
        }: _*)
      }
    }

    private def executeSetAlias(newIndexName: String): Future[Done] = {
      val futureAliasies: Future[GetAliasResponse] = client.execute {
        getAlias(Seq(elasticsearchConfiguration.aliasName))
      }

      futureAliasies.onComplete {
        case Success(getAliasResponse) => addAndRemoveAlias(newIndexName, getAliasResponse.keys.toSeq)
        case Failure(e)                => logger.error("fail to retrieve ES alias", e)
        case _                         => logger.error("fail to retrieve ES alias")
      }

      Future.successful(Done)
    }

    private def executeCreateIndex(newIndexName: String): Future[CreateIndexResponse] = {
      elasticsearchConfiguration.client.execute {
        createIndex(newIndexName).source(elasticsearchConfiguration.elasticsearchMapping)
      }
    }

    private def executeIndexProposals(newIndexName: String): Future[Done] = {
      val start = System.currentTimeMillis()
      val parallelism = 5
      val result = readJournal
        .currentPersistenceIds()
        .mapAsync(parallelism) { persistenceId =>
          getIndexedProposal(ProposalId(persistenceId))
        }
        .filter(_.isDefined)
        .map(_.get)
        .mapAsync(parallelism) { indexedProposal =>
          elasticsearchProposalAPI
            .indexProposal(indexedProposal, Some(IndexAndType(newIndexName, ProposalSearchEngine.proposalIndexName)))
            .recoverWith {
              case e =>
                logger.error("indexing proposal failed", e)
                Future.successful(Done)
            }
        }
        .runForeach { done =>
          logger.debug("proposal flow ended with result {}", done)
        }

      result.onComplete {
        case Success(_) => logger.info("proposal indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) => logger.error(s"proposal indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }

    private def executeIndexSequences(newIndexName: String): Future[Done] = {
      val start = System.currentTimeMillis()

      val parallelism = 5
      val result = readJournal
        .currentPersistenceIds()
        .mapAsync(parallelism) { persistenceId =>
          getIndexedSequence(SequenceId(persistenceId))
        }
        .filter(_.isDefined)
        .map(_.get)
        .mapAsync(parallelism) { indexedSequence =>
          elasticsearchSequenceAPI
            .indexSequence(indexedSequence, Some(IndexAndType(newIndexName, SequenceSearchEngine.sequenceIndexName)))
            .recoverWith {
              case e =>
                logger.error("indexing sequence failed", e)
                Future.successful(Done)
            }
        }
        .runForeach { done =>
          logger.debug("sequence flow ended with result {}", done)
        }

      result.onComplete {
        case Success(_) => logger.info("Sequence indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) => logger.error(s"Sequence indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }
  }

  private def executeIndexIdeas(newIndexName: String): Future[Done] = {
    val start = System.currentTimeMillis()

    val result = persistentIdeaService.findAll(IdeaFiltersRequest.empty)
      .map { ideas =>
        logger.info(s"Ideas to index: ${ideas.size}")
        ideas.foreach { idea =>
          elasticsearchIdeaAPI
            .indexIdea(IndexedIdea.createFromIdea(idea), Some(IndexAndType(newIndexName, IdeaSearchEngine.ideaIndexName)))
            .recoverWith {
              case e =>
                logger.error("indexing idea failed", e)
                Future.successful(Done)
            }
        }
      }

    result.onComplete {
      case Success(_) => logger.info("Idea indexation success in {} ms", System.currentTimeMillis() - start)
      case Failure(e) => logger.error(s"Idea indexation failed in ${System.currentTimeMillis() - start} ms", e)
    }

    Future.successful(Done)
  }

  private def retrieveTags(tags: Seq[TagId]): Future[Option[Seq[Tag]]] = {
    tagService
      .findEnabledByTagIds(tags)
      .map(Some(_))
  }
  private def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
    themeService
      .findAll()
      .map(_.filter(theme => themeIds.contains(theme.themeId)))
      .map(Some(_))
  }

  private def getIndexedProposal(proposalId: ProposalId): Future[Option[IndexedProposal]] = {

    val futureMayBeProposal: Future[Option[Proposal]] =
      proposalCoordinatorService.getProposal(proposalId)

    val maybeResult: OptionT[Future, IndexedProposal] = for {
      proposal <- OptionT(futureMayBeProposal)
      user     <- OptionT(userService.getUser(proposal.author))
      tags     <- OptionT(retrieveTags(proposal.tags))
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
          postalCode = user.profile.flatMap(_.postalCode),
          age = user.profile
            .flatMap(_.dateOfBirth)
            .map(date => ChronoUnit.YEARS.between(date, LocalDate.now()).toInt)
        ),
        country = proposal.creationContext.country.getOrElse("FR"),
        language = proposal.creationContext.language.getOrElse("fr"),
        themeId = proposal.theme,
        tags = tags,
        ideaId = proposal.idea,
        operationId = proposal.operation
      )
    }

    maybeResult.value
  }

  private def getIndexedSequence(sequenceId: SequenceId): Future[Option[IndexedSequence]] = {

    val futureMayBeSequence: Future[Option[Sequence]] =
      sequenceCoordinatorService.getSequence(sequenceId)

    val maybeResult: OptionT[Future, IndexedSequence] = for {
      sequence <- OptionT(futureMayBeSequence)
      tags     <- OptionT(retrieveTags(sequence.tagIds))
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
        tags = tags,
        themes = themes.map(theme => IndexedSequenceTheme(themeId = theme.themeId, translation = theme.translations)),
        operationId = sequence.operationId,
        proposals = sequence.proposalIds.map(IndexedSequenceProposalId.apply),
        searchable = sequence.searchable
      )
    }

    maybeResult.value
  }
}
