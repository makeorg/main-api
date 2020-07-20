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

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.{DefaultPersistentIdeaServiceComponent, IdeaSearchEngine, IdeaSearchEngineComponent}
import org.make.api.operation._
import org.make.api.organisation.{OrganisationSearchEngine, OrganisationService, OrganisationServiceComponent}
import org.make.api.post.{PostSearchEngine, PostService, PostServiceComponent}
import org.make.api.proposal.{
  ProposalCoordinatorService,
  ProposalCoordinatorServiceComponent,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.segment.{SegmentService, SegmentServiceComponent}
import org.make.api.semantic.{SemanticComponent, SemanticService}
import org.make.api.sequence._
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.tagtype.{DefaultPersistentTagTypeServiceComponent, TagTypeService, TagTypeServiceComponent}
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical.job.{JobCoordinatorService, JobCoordinatorServiceComponent}
import org.make.api.user._
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.make.core.RequestContext
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class IndexationComponentTest
    extends MakeUnitTest
    with DefaultIndexationComponent
    with ElasticsearchConfigurationComponent
    with ElasticsearchClientComponent
    with StrictLogging
    with ActorSystemComponent
    with ProposalCoordinatorServiceComponent
    with ReadJournalComponent
    with UserServiceComponent
    with OrganisationServiceComponent
    with TagServiceComponent
    with TagTypeServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with ProposalSearchEngineComponent
    with IdeaSearchEngineComponent
    with OperationOfQuestionIndexationStream
    with DefaultPersistentIdeaServiceComponent
    with MakeDBExecutionContextComponent
    with SemanticComponent
    with UserHistoryCoordinatorServiceComponent
    with OperationServiceComponent
    with OperationOfQuestionServiceComponent
    with QuestionServiceComponent
    with PersistentOperationOfQuestionServiceComponent
    with SegmentServiceComponent
    with JobCoordinatorServiceComponent
    with PostServiceComponent
    with PostIndexationStream {

  override lazy val actorSystem: ActorSystem = ActorSystem()
  override val elasticsearchPostAPI: PostSearchEngine = mock[PostSearchEngine]
  override val elasticsearchIdeaAPI: IdeaSearchEngine = mock[IdeaSearchEngine]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val elasticsearchOrganisationAPI: OrganisationSearchEngine = mock[OrganisationSearchEngine]
  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]
  override val userService: UserService = mock[UserService]
  override val organisationService: OrganisationService = mock[OrganisationService]
  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  override val elasticsearchClient: ElasticsearchClient = mock[ElasticsearchClient]
  override def writeExecutionContext: ExecutionContext = mock[ExecutionContext]
  override def readExecutionContext: ExecutionContext = mock[ExecutionContext]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val proposalJournal: MakeReadJournal = mock[MakeReadJournal]
  override val userJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournal]
  override val semanticService: SemanticService = mock[SemanticService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val questionService: QuestionService = mock[QuestionService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]
  override val postService: PostService = mock[PostService]

  override val tagService: TagService = mock[TagService]
  override val jobCoordinatorService: JobCoordinatorService = mock[JobCoordinatorService]
  override val tagTypeService: TagTypeService = mock[TagTypeService]

//  val indexName = "make-index"
  when(elasticsearchConfiguration.ideaAliasName).thenReturn("idea")
  when(elasticsearchConfiguration.organisationAliasName).thenReturn("organisation")
  when(elasticsearchConfiguration.proposalAliasName).thenReturn("proposal")
  when(elasticsearchConfiguration.operationOfQuestionAliasName).thenReturn("operation-of-question")

  override val segmentService: SegmentService = mock[SegmentService]

  private val ideaHash = "idea#hash"
  private val organisationHash = "organisation#hash"
  private val proposalHash = "proposal#hash"
  private val operationOfQuestionHash = "operationOfQuestion#hash"

  when(segmentService.resolveSegment(any[RequestContext])).thenReturn(Future.successful(None))

  when(elasticsearchClient.getHashFromIndex(ideaHash)).thenReturn(ideaHash)
  when(elasticsearchClient.getHashFromIndex(organisationHash)).thenReturn(organisationHash)
  when(elasticsearchClient.getHashFromIndex(proposalHash)).thenReturn(proposalHash)
  when(elasticsearchClient.getHashFromIndex(operationOfQuestionHash)).thenReturn(operationOfQuestionHash)

  when(elasticsearchClient.hashForAlias(eqTo("idea"))).thenReturn(ideaHash)
  when(elasticsearchClient.hashForAlias(eqTo("organisation"))).thenReturn(organisationHash)
  when(elasticsearchClient.hashForAlias(eqTo("proposal"))).thenReturn(proposalHash)
  when(elasticsearchClient.hashForAlias(eqTo("operation-of-question")))
    .thenReturn(operationOfQuestionHash)

  when(elasticsearchConfiguration.connectionString).thenReturn("fake:3232")

  Feature("Check if ES schema is up to date") {
    Scenario("schema is up to date") {
      Given("a defined hash of indices")
      when(elasticsearchClient.getCurrentIndicesName)
        .thenReturn(Future.successful(Seq(ideaHash, organisationHash, proposalHash, operationOfQuestionHash)))
      When("I ask which indices are to update")

      val futureSchemaIsUpToDate: Future[Set[EntitiesToIndex]] =
        indexationService.indicesToReindex(
          forceIdeas = false,
          forceOrganisations = false,
          forceProposals = false,
          forceOperationOfQuestions = false
        )

      Then("no indices should be returned")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { indicesToUpdate =>
        indicesToUpdate.isEmpty shouldBe true
      }
    }

    Scenario("schema is up to date but force proposal indexation") {
      Given("a defined hash of indices")
      when(elasticsearchClient.getCurrentIndicesName)
        .thenReturn(Future.successful(Seq(ideaHash, organisationHash, proposalHash, operationOfQuestionHash)))
      When("I ask which indices are to update and force the proposal indexation")

      val futureSchemaIsUpToDate: Future[Set[EntitiesToIndex]] =
        indexationService.indicesToReindex(
          forceIdeas = false,
          forceOrganisations = false,
          forceProposals = true,
          forceOperationOfQuestions = false
        )

      Then("the proposal")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { indicesToUpdate =>
        indicesToUpdate.size shouldBe 1
        indicesToUpdate.contains(IndexProposals) shouldBe true
      }
    }

    Scenario("schema is not up to date on the proposal index") {
      Given("a defined hash of indices and an old hash for the proposal index")
      when(elasticsearchClient.hashForAlias(eqTo("proposal"))).thenReturn("old-hash")
      when(elasticsearchClient.getCurrentIndicesName)
        .thenReturn(Future.successful(Seq(ideaHash, organisationHash, proposalHash, operationOfQuestionHash)))
      When("I ask which indices are to update")

      val futureSchemaIsUpToDate: Future[Set[EntitiesToIndex]] =
        indexationService.indicesToReindex(
          forceIdeas = false,
          forceOrganisations = false,
          forceProposals = false,
          forceOperationOfQuestions = false
        )

      Then("the resullt should be true")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { indicesToUpdate =>
        indicesToUpdate.size shouldBe 1
        indicesToUpdate.contains(IndexProposals) shouldBe true
      }
    }
  }
}
