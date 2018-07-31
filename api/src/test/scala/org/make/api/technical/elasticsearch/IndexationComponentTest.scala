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
import org.make.api.proposal.{
  ProposalCoordinatorService,
  ProposalCoordinatorServiceComponent,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.semantic.{SemanticComponent, SemanticService}
import org.make.api.sequence.{
  SequenceCoordinatorService,
  SequenceCoordinatorServiceComponent,
  SequenceSearchEngine,
  SequenceSearchEngineComponent
}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.tagtype.DefaultPersistentTagTypeServiceComponent
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.theme.{PersistentThemeService, PersistentThemeServiceComponent}
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
    with DefaultPersistentTagTypeServiceComponent
    with PersistentThemeServiceComponent
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
  override val proposalJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sequenceJournal: MakeReadJournal = mock[MakeReadJournal]
  override val userJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournal]
  override val semanticService: SemanticService = mock[SemanticService]
  override val persistentThemeService: PersistentThemeService = mock[PersistentThemeService]
  override val tagService: TagService = mock[TagService]

//  val indexName = "make-index"
  when(elasticsearchConfiguration.ideaAliasName).thenReturn("idea")
  when(elasticsearchConfiguration.proposalAliasName).thenReturn("proposal")
  when(elasticsearchConfiguration.sequenceAliasName).thenReturn("sequence")

  private val ideaHash = "idea#hash"
  private val proposalHash = "proposal#hash"
  private val sequenceHash = "sequence#hash"

  when(elasticsearchConfiguration.getHashFromIndex(ideaHash)).thenReturn(ideaHash)
  when(elasticsearchConfiguration.getHashFromIndex(proposalHash)).thenReturn(proposalHash)
  when(elasticsearchConfiguration.getHashFromIndex(sequenceHash)).thenReturn(sequenceHash)

  when(elasticsearchConfiguration.hashForAlias(ArgumentMatchers.eq("idea"))).thenReturn(ideaHash)
  when(elasticsearchConfiguration.hashForAlias(ArgumentMatchers.eq("proposal"))).thenReturn(proposalHash)
  when(elasticsearchConfiguration.hashForAlias(ArgumentMatchers.eq("sequence"))).thenReturn(sequenceHash)

  when(elasticsearchConfiguration.connectionString).thenReturn("fake:3232")

  feature("Check if ES schema is up to date") {
    scenario("schema is up to date") {
      Given("a defined hash of indices")
      when(elasticsearchConfiguration.getCurrentIndicesName)
        .thenReturn(Future.successful(Seq(ideaHash, proposalHash, sequenceHash)))
      When("I ask which indices are to update")

      val futureSchemaIsUpToDate: Future[Set[EntitiesToIndex]] =
        indexationService.indicesToReindex(forceIdeas = false, forceProposals = false, forceSequences = false)

      Then("no indices should be returned")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { indicesToUpdate =>
        indicesToUpdate.isEmpty shouldBe true
      }
    }

    scenario("schema is up to date but force proposal indexation") {
      Given("a defined hash of indices")
      when(elasticsearchConfiguration.getCurrentIndicesName)
        .thenReturn(Future.successful(Seq(ideaHash, proposalHash, sequenceHash)))
      When("I ask which indices are to update and force the proposal indexation")

      val futureSchemaIsUpToDate: Future[Set[EntitiesToIndex]] =
        indexationService.indicesToReindex(forceIdeas = false, forceProposals = true, forceSequences = false)

      Then("the proposal")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { indicesToUpdate =>
        indicesToUpdate.size shouldBe 1
        indicesToUpdate.contains(IndexProposals) shouldBe true
      }
    }

    scenario("schema is not up to date on the proposal index") {
      Given("a defined hash of indices and an old hash for the proposal index")
      when(elasticsearchConfiguration.hashForAlias(ArgumentMatchers.eq("proposal"))).thenReturn("old-hash")
      when(elasticsearchConfiguration.getCurrentIndicesName)
        .thenReturn(Future.successful(Seq(ideaHash, proposalHash, sequenceHash)))
      When("I ask which indices are to update")

      val futureSchemaIsUpToDate: Future[Set[EntitiesToIndex]] =
        indexationService.indicesToReindex(forceIdeas = false, forceProposals = false, forceSequences = false)

      Then("the resullt should be true")
      whenReady(futureSchemaIsUpToDate, Timeout(3.seconds)) { indicesToUpdate =>
        indicesToUpdate.size shouldBe 1
        indicesToUpdate.contains(IndexProposals) shouldBe true
      }
    }
  }
}
