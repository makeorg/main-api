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
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.{ActorSystemComponent, ActorSystemTypedComponent, DatabaseTest, DefaultConfigComponent}
import org.make.api.docker.{DockerCassandraService, DockerElasticsearchService}
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
  DefaultReadJournalComponent,
  DefaultSpawnActorServiceComponent,
  DownloadService,
  DownloadServiceComponent,
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
import org.make.core.sequence.{
  SequenceConfiguration,
  SequenceId,
  SpecificSequenceConfiguration,
  SpecificSequenceConfigurationId
}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class SequenceServiceIT
    extends TestKit(SequenceServiceIT.actorSystem)
    with DatabaseTest
    with DockerCassandraService
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
    with DefaultReadJournalComponent
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
    with SemanticComponent
    with SequenceConfigurationComponent
    with SessionHistoryCoordinatorComponent
    with SpawnActorRefComponent
    with StorageServiceComponent
    with TopIdeaCommentServiceComponent
    with TopIdeaServiceComponent
    with UserHistoryCoordinatorComponent
    with UserRegistrationValidatorComponent {

  override val adminEmail = "admin@make.org"
  override val cassandraExposedPort: Int = SequenceServiceIT.cassandraExposedPort
  override val cockroachExposedPort: Int = 40024
  override val elasticsearchExposedPort: Int = 30007

  override implicit val actorSystem: ExtendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
  override val actorSystemTyped: typed.ActorSystem[Nothing] = actorSystem.toTyped

  override val jobCoordinator: typed.ActorRef[JobActor.Protocol.Command] = JobCoordinator(actorSystemTyped, 1.second)
  override lazy val proposalCoordinator: typed.ActorRef[ProposalCommand] =
    ProposalCoordinator(actorSystemTyped, sessionHistoryCoordinatorService, 1.second, idGenerator)
  override lazy val sessionHistoryCoordinator: ActorRef =
    actorSystem.actorOf(SessionHistoryCoordinator.props(userHistoryCoordinator, idGenerator))
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

  private var questionId: QuestionId = null
  private val requestContext = RequestContext.empty.copy(sessionId = idGenerator.nextSessionId())
  private def specificSequence =
    SpecificSequenceConfiguration.mainSequenceDefault(SpecificSequenceConfigurationId(questionId.value))
  private def sequenceConfiguration =
    SequenceConfiguration.default.copy(
      sequenceId = SequenceId(questionId.value),
      questionId = questionId,
      mainSequence = specificSequence,
      controversial = specificSequence,
      popular = specificSequence,
      keyword = specificSequence,
      newProposalsVoteThreshold = 10,
      testedProposalsEngagementThreshold = None
    )

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
    val fixturesResult = whenReady(fixturesService.generate(None, None, Some(500)), Timeout(60.seconds))(identity)
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
              .searchProposals(SearchQuery(limit = Some(count.toInt / 10)))
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
    Scenario("standard behaviour") {
      whenReady(
        sequenceService.startNewSequence(None, None, None, questionId, Nil, None, requestContext),
        Timeout(60.seconds)
      ) { result =>
        result.proposals.size should be > 0
        result.proposals.map(p => getScorer(getProposal(p)).zone).distinct.size should be > 1
        result.proposals.foreach(_.votes.foreach(_.hasVoted shouldBe false))
      }
    }

    for (zone <- Zone.values) {
      Scenario(s"$zone zone behaviour") {
        whenReady(
          sequenceService.startNewSequence(Some(zone), None, None, questionId, Nil, None, requestContext),
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

    Scenario("tag behaviour") {
      val futureSequenceTags = for {
        proposals <- elasticsearchProposalAPI.searchProposals(SearchQuery(limit = Some(10)))
        tags = proposals.results.flatMap(_.tags.map(_.tagId)).take(2)
        sequence <- sequenceService.startNewSequence(None, None, None, questionId, Nil, Some(tags), requestContext)
      } yield (sequence, tags)
      whenReady(futureSequenceTags, Timeout(60.seconds)) {
        case (sequence, tags) if tags.nonEmpty =>
          sequence.proposals.size should be > 0
          sequence.proposals.foreach(proposal => tags.intersect(proposal.tags.map(_.tagId)).size should be > 0)
        case (sequence, _) =>
          sequence.proposals.size should be(0)
      }
    }

    Scenario("keyword behaviour") {
      val futureSequenceKey = for {
        proposals <- elasticsearchProposalAPI.searchProposals(SearchQuery(limit = Some(10)))
        key = proposals.results.flatMap(_.keywords.map(_.key)).headOption
        sequence <- sequenceService.startNewSequence(None, key, None, questionId, Nil, None, requestContext)
      } yield (sequence, key)
      whenReady(futureSequenceKey, Timeout(60.seconds)) {
        case (sequence, Some(key)) =>
          sequence.proposals.size should be > 0
          sequence.proposals.foreach(_.keywords.map(_.key) should contain(key))
        case (_, None) => succeed
      }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

}

object SequenceServiceIT {
  val cassandraExposedPort: Int = 15001
  val configuration: String =
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://SequenceServiceIT@localhost:25520"]
       |  cluster.jmx.multi-mbeans-in-same-jvm = on
       |
       |  persistence {
       |    cassandra {
       |      events-by-tag.enabled = false
       |      journal {
       |        keyspace = "fake"
       |        keyspace-autocreate = true
       |        tables-autocreate = true
       |        replication-factor = 1
       |      }
       |      snapshot {
       |        keyspace = "fake"
       |        keyspace-autocreate = true
       |        tables-autocreate = true
       |        replication-factor = 1
       |      }
       |    }
       |
       |    journal {
       |      plugin = "make-api.event-sourcing.proposals.journal"
       |    }
       |    snapshot-store {
       |      plugin = "make-api.event-sourcing.proposals.snapshot"
       |    }
       |    role = "worker"
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
       |
       |datastax-java-driver.basic {
       |  contact-points = ["127.0.0.1:$cassandraExposedPort"]
       |  load-balancing-policy.local-datacenter = "datacenter1"
       |}
       |
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
       |
       |  kafka {
       |    connection-string = "nowhere:-1"
       |    schema-registry = "http://nowhere:-1"
       |  }
       |
       |  cookie-session.lifetime = "600 milliseconds"
       |}
    """.stripMargin

  val fullConfiguration: Config =
    ConfigFactory
      .parseString(configuration)
      .withFallback(ConfigFactory.load("default-application.conf"))
      .resolve()

  val actorSystem = ActorSystem("SequenceServiceIT", fullConfiguration)
}
