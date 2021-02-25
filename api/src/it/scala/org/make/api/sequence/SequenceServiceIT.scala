/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.sequence

import akka.actor.{typed, ActorRef, ActorSystem, ExtendedActorSystem}
import akka.actor.typed.SpawnProtocol
import akka.actor.typed.scaladsl.adapter._
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.{ActorSystemComponent, ActorSystemTypedComponent, DatabaseTest, DefaultConfigComponent}
import org.make.api.docker.DockerElasticsearchService
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.feature.{
  DefaultActiveFeatureServiceComponent,
  DefaultFeatureServiceComponent,
  DefaultPersistentActiveFeatureServiceComponent,
  DefaultPersistentFeatureServiceComponent
}
import org.make.api.idea.{
  DefaultIdeaMappingServiceComponent,
  DefaultPersistentIdeaMappingServiceComponent,
  DefaultPersistentIdeaServiceComponent,
  IdeaSearchEngine,
  IdeaService,
  IdeaServiceComponent,
  PersistentTopIdeaService,
  PersistentTopIdeaServiceComponent,
  TopIdeaService,
  TopIdeaServiceComponent
}
import org.make.api.idea.topIdeaComments.{
  PersistentTopIdeaCommentService,
  PersistentTopIdeaCommentServiceComponent,
  TopIdeaCommentService,
  TopIdeaCommentServiceComponent
}
import org.make.api.operation._
import org.make.api.organisation.{DefaultOrganisationSearchEngineComponent, DefaultOrganisationServiceComponent}
import org.make.api.partner.{DefaultPartnerServiceComponent, DefaultPersistentPartnerServiceComponent}
import org.make.api.personality.{
  DefaultPersistentQuestionPersonalityServiceComponent,
  DefaultQuestionPersonalityServiceComponent
}
import org.make.api.post.{PostSearchEngine, PostService, PostServiceComponent}
import org.make.api.proposal._
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.api.question.{DefaultPersistentQuestionServiceComponent, DefaultQuestionServiceComponent}
import org.make.api.segment.DefaultSegmentServiceComponent
import org.make.api.semantic.{SemanticComponent, SemanticService}
import org.make.api.sessionhistory.{
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCoordinator,
  SessionHistoryCoordinatorComponent
}
import org.make.api.tag.{DefaultPersistentTagServiceComponent, DefaultTagServiceComponent}
import org.make.api.tagtype.{DefaultPersistentTagTypeServiceComponent, DefaultTagTypeServiceComponent}
import org.make.api.technical.{
  DefaultEventBusServiceComponent,
  DefaultIdGeneratorComponent,
  DefaultSpawnActorServiceComponent,
  DownloadService,
  DownloadServiceComponent,
  ReadJournalComponent,
  SpawnActorRefComponent
}
import org.make.api.technical.auth.{
  DefaultTokenGeneratorComponent,
  DefaultUserTokenGeneratorComponent,
  MakeDataHandler,
  MakeDataHandlerComponent
}
import org.make.api.technical.crm.{CrmService, CrmServiceComponent}
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  DefaultElasticsearchConfigurationComponent,
  DefaultIndexationComponent
}
import org.make.api.technical.generator.fixtures.DefaultFixturesServiceComponent
import org.make.api.technical.job.{
  DefaultJobCoordinatorServiceComponent,
  JobActor,
  JobCoordinator,
  JobCoordinatorComponent
}
import org.make.api.technical.security.{DefaultSecurityConfigurationComponent, SecurityHelper}
import org.make.api.technical.storage.{StorageService, StorageServiceComponent}
import org.make.api.user.{
  DefaultPersistentUserServiceComponent,
  DefaultPersistentUserToAnonymizeServiceComponent,
  DefaultUserServiceComponent
}
import org.make.api.user.validation.{UserRegistrationValidator, UserRegistrationValidatorComponent}
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.core.{DefaultDateHelperComponent, RequestContext}
import org.make.core.job.Job
import org.make.core.job.Job.JobId
import org.make.core.proposal.indexed.{SequencePool, Zone}
import org.make.core.proposal.{Proposal, ProposalStatus, SearchFilters, SearchQuery, StatusSearchFilter, VoteKey}
import org.make.core.question.QuestionId
import org.make.core.sequence.{SequenceConfiguration, SequenceId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class SequenceServiceIT
    extends TestKit(SequenceServiceIT.actorSystem)
    with DatabaseTest
    with DockerElasticsearchService
    with ActorSystemComponent
    with ActorSystemTypedComponent
    with CrmServiceComponent
    with DefaultActiveFeatureServiceComponent
    with DefaultConfigComponent
    with DefaultDateHelperComponent
    with DefaultElasticsearchClientComponent
    with DefaultElasticsearchConfigurationComponent
    with DefaultEventBusServiceComponent
    with DefaultFeatureServiceComponent
    with DefaultFixturesServiceComponent
    with DefaultIdeaMappingServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultIndexationComponent
    with DefaultJobCoordinatorServiceComponent
    with DefaultOrganisationSearchEngineComponent
    with DefaultOperationOfQuestionSearchEngineComponent
    with DefaultOperationOfQuestionServiceComponent
    with DefaultOperationServiceComponent
    with DefaultOrganisationServiceComponent
    with DefaultPartnerServiceComponent
    with DefaultPersistentActiveFeatureServiceComponent
    with DefaultPersistentFeatureServiceComponent
    with DefaultPersistentIdeaMappingServiceComponent
    with DefaultPersistentIdeaServiceComponent
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentPartnerServiceComponent
    with DefaultPersistentQuestionPersonalityServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentUserToAnonymizeServiceComponent
    with DefaultProposalCoordinatorServiceComponent
    with DefaultProposalSearchEngineComponent
    with DefaultProposalServiceComponent
    with DefaultQuestionPersonalityServiceComponent
    with DefaultQuestionServiceComponent
    with DefaultSecurityConfigurationComponent
    with DefaultSegmentServiceComponent
    with DefaultSelectionAlgorithmComponent
    with DefaultSequenceServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DefaultSpawnActorServiceComponent
    with DefaultTagServiceComponent
    with DefaultTagTypeServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultUserHistoryCoordinatorServiceComponent
    with DefaultUserServiceComponent
    with DefaultUserTokenGeneratorComponent
    with DownloadServiceComponent
    with IdeaServiceComponent
    with JobCoordinatorComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with PersistentSequenceConfigurationComponent
    with PersistentTopIdeaCommentServiceComponent
    with PersistentTopIdeaServiceComponent
    with PostServiceComponent
    with ProposalCoordinatorComponent
    with ReadJournalComponent
    with SemanticComponent
    with SequenceConfigurationComponent
    with SessionHistoryCoordinatorComponent
    with SpawnActorRefComponent
    with StorageServiceComponent
    with TopIdeaCommentServiceComponent
    with TopIdeaServiceComponent
    with UserHistoryCoordinatorComponent
    with UserRegistrationValidatorComponent {

  override type MakeReadJournal = InMemoryReadJournal

  override val adminEmail = "admin@make.org"
  override val cockroachExposedPort: Int = 40024
  override val elasticsearchExposedPort: Int = 30007

  override implicit val actorSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
  override val actorSystemTyped: typed.ActorSystem[Nothing] = actorSystem.toTyped

  override val jobCoordinator: typed.ActorRef[JobActor.Protocol.Command] = JobCoordinator(actorSystemTyped, 1.second)
  override lazy val proposalCoordinator: ActorRef =
    actorSystem.actorOf(ProposalCoordinator.props(sessionHistoryCoordinatorService, 1.second))
  override lazy val sessionHistoryCoordinator: ActorRef =
    actorSystem.actorOf(SessionHistoryCoordinator.props(userHistoryCoordinator))
  override val spawnActorRef: typed.ActorRef[SpawnProtocol.Command] =
    actorSystemTyped.systemActorOf(SpawnProtocol(), "spawner")
  override lazy val userHistoryCoordinator: ActorRef = actorSystem.actorOf(UserHistoryCoordinator.props)

  override val makeSettings: MakeSettings = mock[MakeSettings]
  when(makeSettings.maxHistoryProposalsPerPage).thenReturn(10)
  when(makeSettings.resetTokenExpiresIn).thenReturn(1.day)
  when(makeSettings.resetTokenB2BExpiresIn).thenReturn(1.day)
  when(makeSettings.validationTokenExpiresIn).thenReturn(1.day)

  override val semanticService: SemanticService = mock[SemanticService]
  when(semanticService.indexProposals(any)).thenReturn(Future.unit)

  override val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    mock[PersistentSequenceConfigurationService]
  when(persistentSequenceConfigurationService.persist(any)).thenReturn(Future.successful(true))
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]
  when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(any)).thenAnswer { _: QuestionId =>
    Future.successful(sequenceConfiguration)
  }

  override val proposalJournal: MakeReadJournal = new InMemoryReadJournal(
    SequenceServiceIT.fullConfiguration.getConfig("journal")
  )

  override val userRegistrationValidator: UserRegistrationValidator = mock[UserRegistrationValidator]
  when(userRegistrationValidator.canRegister(any)).thenReturn(Future.successful(true))

  override def crmService: CrmService = ???
  override def oauth2DataHandler: MakeDataHandler = ???
  override def downloadService: DownloadService = ???
  override def storageService: StorageService = ???
  override def ideaService: IdeaService = ???
  override def topIdeaService: TopIdeaService = ???
  override def topIdeaCommentService: TopIdeaCommentService = ???
  override def persistentTopIdeaService: PersistentTopIdeaService = ???
  override def persistentTopIdeaCommentService: PersistentTopIdeaCommentService = ???
  override def postService: PostService = ???
  override def elasticsearchPostAPI: PostSearchEngine = ???
  override def elasticsearchIdeaAPI: IdeaSearchEngine = ???
  override def sessionJournal: MakeReadJournal = ???
  override def userJournal: MakeReadJournal = ???

  private var questionId: QuestionId = null
  private val requestContext = RequestContext.empty.copy(sessionId = idGenerator.nextSessionId())
  private def sequenceConfiguration =
    SequenceConfiguration(sequenceId = SequenceId(questionId.value), questionId = questionId)

  override def beforeAll(): Unit = {

    def reindex(): Unit = {
      indexationService.reindexData(false, false, true, false)
      while (!Await
               .result(jobCoordinatorService.get(JobId.Reindex), 1.second)
               .get
               .status
               .isInstanceOf[Job.JobStatus.Finished]) {
        Thread.sleep(1000)
      }
    }

    super.beforeAll()
    // initialize ES
    Await.result(elasticsearchClient.initialize(), 10.seconds)
    // generate a question with data (proposals, votes, organisations…)
    val fixturesResult = whenReady(fixturesService.generate(None, None), Timeout(60.seconds))(identity)
    questionId = fixturesResult.questionId
    // index the proposals that we just created, so we can find them
    reindex()
    // vote on some proposals
    whenReady(
      elasticsearchProposalAPI
        .countProposals(
          SearchQuery(filters = Some(SearchFilters(status = Some(StatusSearchFilter(Seq(ProposalStatus.Accepted))))))
        )
        .map(
          count =>
            elasticsearchProposalAPI
              .searchProposals(SearchQuery(limit = Some(count.toInt / 3)))
              .flatMap(
                result =>
                  Future.traverse(result.results)(
                    proposal =>
                      proposalService.voteProposal(
                        proposal.id,
                        None,
                        requestContext,
                        VoteKey.Agree,
                        Some(
                          SecurityHelper.generateProposalKeyHash(
                            proposal.id,
                            requestContext.sessionId,
                            requestContext.location,
                            securityConfiguration.secureVoteSalt
                          )
                        )
                      )
                  )
              )
        ),
      Timeout(60.seconds)
    )(_ => ())
    // reindex new votes
    reindex()
  }

  private def getProposal(proposal: ProposalResponse): Proposal =
    whenReady(proposalService.getEventSourcingProposal(proposal.id, RequestContext.empty), Timeout(5.seconds))(_.get)

  private def getScorer(proposal: Proposal): ProposalScorer =
    ProposalScorer(proposal.votes, VotesCounter.SequenceVotesCounter, sequenceConfiguration.nonSequenceVotesWeight)

  Feature("Start a sequence") {
    Scenario("no zone") {
      whenReady(
        sequenceService.startNewSequence(None, None, questionId, Nil, None, requestContext),
        Timeout(60.seconds)
      ) { result =>
        result.proposals.size should be > 0
        result.proposals.map(p => getScorer(getProposal(p)).zone).distinct.size should be > 1
        result.proposals.foreach(_.votes.foreach(_.hasVoted shouldBe false))
      }
    }
    for (zone <- Zone.values) {
      Scenario(s"$zone zone") {
        whenReady(
          sequenceService.startNewSequence(Some(zone), None, questionId, Nil, None, requestContext),
          Timeout(60.seconds)
        ) { result =>
          result.proposals.size should be > 0
          result.proposals.foreach { p =>
            val proposal = getProposal(p)
            val scorer = getScorer(proposal)
            scorer.zone shouldBe zone
            scorer.pool(sequenceConfiguration, proposal.status) shouldBe SequencePool.Tested
            p.votes.foreach(_.hasVoted shouldBe false)
          }
        }
      }
    }
  }

}

object SequenceServiceIT {
  val configuration: String =
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://SequenceServiceIT@localhost:25520"]
       |  cluster.jmx.multi-mbeans-in-same-jvm = on
       |
       |  persistence {
       |    journal.plugin = "inmemory-journal"
       |    snapshot-store.plugin = "inmemory-snapshot-store"
       |  }
       |
       |  remote.artery.canonical {
       |    port = 25520
       |    hostname = "localhost"
       |  }
       |
       |  test {
       |    timefactor = 10.0
       |  }
       |}
       |make-api {
       |  elasticSearch {
       |    connection-string = "localhost:30007"
       |    index-name = "make"
       |    idea-alias-name = "idea"
       |    organisation-alias-name = "organisation"
       |    proposal-alias-name = "proposal"
       |    operation-of-question-alias-name = "operation-of-question"
       |    post-alias-name = "post"
       |  }
       |  event-sourcing {
       |    jobs {
       |      read-journal = $${inmemory-journal}
       |      snapshot-store = $${inmemory-snapshot-store}
       |    }
       |    proposals {
       |      read-journal = $${inmemory-journal}
       |      snapshot-store = $${inmemory-snapshot-store}
       |    }
       |    sequences {
       |      read-journal = $${inmemory-journal}
       |      snapshot-store = $${inmemory-snapshot-store}
       |    }
       |    sessions {
       |      read-journal = $${inmemory-journal}
       |      snapshot-store = $${inmemory-snapshot-store}
       |    }
       |    users {
       |      read-journal = $${inmemory-journal}
       |      snapshot-store = $${inmemory-snapshot-store}
       |    }
       |  }
       |
       |  kafka {
       |    connection-string = "nowhere:-1"
       |    schema-registry = "http://nowhere:-1"
       |  }
       |
       |  cookie-session.lifetime = "600 milliseconds"
       |}
       |journal {
       |  ask-timeout = "5 seconds"
       |  max-buffer-size = "1000"
       |  offset-mode = "sequence"
       |  refresh-interval = "5 seconds"
       |  write-plugin = "make-api.event-sourcing.proposals.read-journal"
       |}
    """.stripMargin

  val fullConfiguration: Config =
    ConfigFactory
      .parseString(configuration)
      .withFallback(ConfigFactory.load("default-application.conf"))
      .resolve()

  val actorSystem = ActorSystem("SequenceServiceIT", fullConfiguration)
}
