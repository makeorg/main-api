package org.make.api.technical.elasticsearch

import java.time.LocalDate
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
import org.make.api.idea._
import org.make.api.proposal.{ProposalCoordinatorServiceComponent, ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.semantic.SemanticComponent
import org.make.api.sequence.{SequenceCoordinatorServiceComponent, SequenceSearchEngine, SequenceSearchEngineComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.tagtype.PersistentTagTypeServiceComponent
import org.make.api.technical.ReadJournalComponent
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.api.{migrations, ActorSystemComponent}
import org.make.core.idea.indexed.IndexedIdea
import org.make.core.proposal.indexed.{Author, IndexedProposal, IndexedVote, Context => ProposalContext}
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.sequence.indexed.{
  IndexedSequence,
  IndexedSequenceProposalId,
  IndexedSequenceTheme,
  Context => SequenceContext
}
import org.make.core.sequence.{Sequence, SequenceId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait IndexationComponent {
  def indexationService: IndexationService
}

trait IndexationService {
  def reindexData(force: Boolean): Future[Done]
  def schemaIsUpToDate(): Future[Boolean]
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

    override def reindexData(force: Boolean): Future[Done] = {
      logger.info(s"Elasticsearch Reindexation: Check schema is up to date - force $force")

      schemaIsUpToDate().map { isUpToDate =>
        logger.info(s"Elasticsearch Reindexation: Check schema is up to date - isUpToDate $isUpToDate")
        if (!isUpToDate || force) {
          val newIndexName = elasticsearchConfiguration.createIndexName
          logger.info("Elasticsearch Reindexation: Begin")

          for {
            _      <- executeCreateIndex(newIndexName)
            _      <- executeIndexProposalsAndSequences(newIndexName)
            _      <- executeIndexIdeas(newIndexName)
            result <- executeSetAlias(newIndexName)
          } yield result
        }
      }

      Future.successful(Done)
    }

    override def schemaIsUpToDate(): Future[Boolean] = {
      val hash = elasticsearchConfiguration.getHashFromIndex(elasticsearchConfiguration.createIndexName)

      elasticsearchConfiguration.getCurrentIndexName.map { index =>
        elasticsearchConfiguration.getHashFromIndex(index) == hash
      }
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
      client.execute {
        getAlias(Seq(elasticsearchConfiguration.aliasName))
      }.onComplete {
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

    private def executeIndexProposalsAndSequences(newIndexName: String): Future[Done] = {
      val start = System.currentTimeMillis()
      val parallelism = 5
      val result = readJournal
        .currentPersistenceIds()
        .mapAsync(parallelism) { persistenceId =>
          getIndexedProposal(ProposalId(persistenceId)).flatMap {
            case result @ Some(_) => Future.successful(result)
            case None =>
              getIndexedSequence(SequenceId(persistenceId))
          }
        }
        .filter(_.isDefined)
        .map(_.get)
        .mapAsync(parallelism) {

          case indexedProposal: IndexedProposal =>
            elasticsearchProposalAPI
              .indexProposal(indexedProposal, Some(IndexAndType(newIndexName, ProposalSearchEngine.proposalIndexName)))
              .recoverWith {
                case e =>
                  logger.error(s"indexing proposal ${indexedProposal.id.value} failed", e)
                  Future.successful(Done)
              }
              .flatMap(_ => semanticService.indexProposal(indexedProposal))
              .recoverWith {
                case e =>
                  logger.error(s"indexing proposal ${indexedProposal.id.value} in semantic failed", e)
                  Future.successful(Done)
              }

          case indexedSequence: IndexedSequence =>
            elasticsearchSequenceAPI
              .indexSequence(indexedSequence, Some(IndexAndType(newIndexName, SequenceSearchEngine.sequenceIndexName)))
              .recoverWith {
                case e =>
                  logger.error(s"indexing sequence ${indexedSequence.id.value} failed", e)
                  Future.successful(Done)
              }
        }
        .runForeach { done =>
          logger.debug("proposal and sequence flow ended with result {}", done)
        }

      result.onComplete {
        case Success(_) => logger.info("proposal indexation success in {} ms", System.currentTimeMillis() - start)
        case Failure(e) => logger.error(s"proposal indexation failed in ${System.currentTimeMillis() - start} ms", e)
      }

      result
    }
  }

  private def executeIndexIdeas(newIndexName: String): Future[Done] = {
    val start = System.currentTimeMillis()

    val result = persistentIdeaService
      .findAll(IdeaFiltersRequest.empty)
      .flatMap { ideas =>
        logger.info(s"Ideas to index: ${ideas.size}")
        migrations.sequentially(ideas) { idea =>
          elasticsearchIdeaAPI
            .indexIdea(
              IndexedIdea.createFromIdea(idea),
              Some(IndexAndType(newIndexName, IdeaSearchEngine.ideaIndexName))
            )
            .map(_ => {})
            .recoverWith {
              case e =>
                logger.error(s"indexing idea with id ${idea.ideaId.value} failed", e)
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

    val futureMayBeSequence: Future[Option[Sequence]] =
      sequenceCoordinatorService.getSequence(sequenceId)

    val maybeResult: OptionT[Future, IndexedSequence] = for {
      sequence <- OptionT(futureMayBeSequence)
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
