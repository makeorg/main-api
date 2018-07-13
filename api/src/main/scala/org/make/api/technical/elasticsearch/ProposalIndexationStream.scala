package org.make.api.technical.elasticsearch

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.proposal.{
  ProposalCoordinatorServiceComponent,
  ProposalScorerHelper,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.semantic.SemanticComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.{
  Author,
  IndexedOrganisationInfo,
  IndexedProposal,
  IndexedScores,
  IndexedVote,
  Context => ProposalContext
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalIndexationStream
    extends IndexationStream
    with ProposalCoordinatorServiceComponent
    with UserServiceComponent
    with TagServiceComponent
    with ProposalSearchEngineComponent
    with SemanticComponent
    with StrictLogging {

  object ProposalStream {
    val maybeIndexedProposal: Flow[String, Option[IndexedProposal], NotUsed] =
      Flow[String].mapAsync(parallelism)(persistenceId => getIndexedProposal(ProposalId(persistenceId)))

    def runIndexProposals(proposalIndexName: String): Flow[Seq[IndexedProposal], Done, NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(parallelism)(proposals => executeIndexProposals(proposals, proposalIndexName))

    def flowIndexProposals(proposalIndexName: String): Flow[String, Done, NotUsed] =
      maybeIndexedProposal
        .via(filterIsDefined[IndexedProposal])
        .via(grouped[IndexedProposal])
        .via(runIndexProposals(proposalIndexName))
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

}
