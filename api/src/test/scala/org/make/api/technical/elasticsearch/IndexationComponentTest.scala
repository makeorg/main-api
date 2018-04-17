package org.make.api.technical.elasticsearch

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.{DefaultPersistentIdeaServiceComponent, IdeaSearchEngine, IdeaSearchEngineComponent}
import org.make.api.proposal.{ProposalCoordinatorService, ProposalCoordinatorServiceComponent, ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.semantic.{SemanticComponent, SemanticService}
import org.make.api.sequence.{SequenceCoordinatorService, SequenceCoordinatorServiceComponent, SequenceSearchEngine, SequenceSearchEngineComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class IndexationComponentTest
  extends MakeUnitTest
  with DefaultIndexationComponent
  with ElasticsearchConfigurationComponent
  with StrictLogging
  with ActorSystemComponent
  with ProposalCoordinatorServiceComponent
  with SequenceCoordinatorServiceComponent
  with ReadJournalComponent
  with UserServiceComponent
  with TagServiceComponent
  with ThemeServiceComponent
  with ProposalSearchEngineComponent
  with SequenceSearchEngineComponent
  with IdeaSearchEngineComponent
  with DefaultPersistentIdeaServiceComponent
  with MakeDBExecutionContextComponent
  with SemanticComponent {

  override lazy val actorSystem: ActorSystem = ActorSystem()
  override val elasticsearchSequenceAPI: SequenceSearchEngine = mock[SequenceSearchEngine]
  override val elasticsearchIdeaAPI: IdeaSearchEngine = mock[IdeaSearchEngine]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val userService: UserService = mock[UserService]
  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  override def writeExecutionContext: ExecutionContext = mock[ExecutionContext]
  override def readExecutionContext: ExecutionContext = mock[ExecutionContext]
  override val sequenceCoordinatorService: SequenceCoordinatorService = mock[SequenceCoordinatorService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val readJournal: MakeReadJournal = mock[MakeReadJournal]
  override val semanticService: SemanticService = mock[SemanticService]
  override val themeService: ThemeService = mock[ThemeService]
  override val tagService: TagService = mock[TagService]

  val indexName = "make-index"
  when(elasticsearchConfiguration.createIndexName)
    .thenReturn(indexName)
  when(elasticsearchConfiguration.connectionString)
    .thenReturn("fake:3232")

  feature("Check if ES schema is up to date") {
    scenario("schema is up to date") {
      Given("a defined hash of index")
      when(elasticsearchConfiguration.getHashFromIndex(ArgumentMatchers.eq(indexName)))
        .thenReturn("sameHash")

      when(elasticsearchConfiguration.getCurrentIndexName)
        .thenReturn(Future.successful(indexName))
      When("i check ES schema is updated")

      val futureSchemaIsUpToDate = indexationService.schemaIsUpToDate()

      Then("the resullt should be true")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { maybeSchemaIsUpToDate =>
        maybeSchemaIsUpToDate shouldBe true
      }
    }

    scenario("schema is not up to date") {
      Given("a defined hash of index")
      when(elasticsearchConfiguration.getHashFromIndex(ArgumentMatchers.eq(indexName)))
        .thenReturn("hash123")
      when(elasticsearchConfiguration.getHashFromIndex(ArgumentMatchers.eq("make-new-index")))
        .thenReturn("hash000")

      when(elasticsearchConfiguration.getCurrentIndexName)
        .thenReturn(Future.successful("make-new-index"))
      When("i check ES schema is updated")

      val futureSchemaIsNotUpToDate = indexationService.schemaIsUpToDate()

      Then("the resullt should be false")
      whenReady(futureSchemaIsNotUpToDate, Timeout(3.seconds)) { maybeSchemaIsNotUpToDate =>
        maybeSchemaIsNotUpToDate shouldBe false
      }
    }
  }
}
