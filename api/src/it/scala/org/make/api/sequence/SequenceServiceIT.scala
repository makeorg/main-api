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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, SpawnProtocol}
import cats.data.NonEmptyList
import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.demographics.{DemographicsCardResponse, DemographicsCardService, DemographicsCardServiceComponent}
import org.make.api.{ActorTest, DatabaseTest, DefaultConfigComponent}
import org.make.api.docker.{DockerCassandraService, DockerElasticsearchService}
import org.make.api.extensions.MakeSettings.DefaultAdmin
import org.make.api.extensions.{
  MailJetConfiguration,
  MailJetConfigurationComponent,
  MakeSettings,
  MakeSettingsComponent
}
import org.make.api.feature.{
  DefaultActiveFeatureServiceComponent,
  DefaultFeatureServiceComponent,
  DefaultPersistentActiveFeatureServiceComponent,
  DefaultPersistentFeatureServiceComponent
}
import org.make.api.idea.{
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
import org.make.api.sequence.SequenceBehaviour.ConsensusParam
import org.make.api.sessionhistory.{
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCommand,
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
  MakeAuthentication,
  MakeDataHandler,
  MakeDataHandlerComponent
}
import org.make.api.technical.crm.{
  CrmService,
  CrmServiceComponent,
  PersistentCrmUserService,
  PersistentCrmUserServiceComponent
}
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
import org.make.api.technical.security.DefaultSecurityConfigurationComponent
import org.make.api.technical.storage.{StorageService, StorageServiceComponent}
import org.make.api.user.{
  DefaultPersistentUserServiceComponent,
  DefaultPersistentUserToAnonymizeServiceComponent,
  DefaultUserServiceComponent
}
import org.make.api.user.validation.{UserRegistrationValidator, UserRegistrationValidatorComponent}
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCommand,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.core.demographics.DemographicsCardId
import org.make.core.{DefaultDateHelperComponent, RequestContext}
import org.make.core.job.Job
import org.make.core.job.Job.JobId
import org.make.core.proposal.indexed.{SequencePool, Zone}
import org.make.core.proposal.{
  Proposal,
  ProposalKeywordKey,
  ProposalStatus,
  SearchFilters,
  SearchQuery,
  StatusSearchFilter,
  VoteKey
}
import org.make.core.question.QuestionId
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  ExplorationSequenceConfigurationId,
  SequenceConfiguration,
  SequenceId,
  SpecificSequenceConfiguration,
  SpecificSequenceConfigurationId
}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class SequenceServiceIT
    extends ActorTest(SequenceServiceIT.actorSystem)
    with DatabaseTest
    with DockerCassandraService
    with DockerElasticsearchService
    with CrmServiceComponent
    with DefaultActiveFeatureServiceComponent
    with DefaultConfigComponent
    with DefaultDateHelperComponent
    with DemographicsCardServiceComponent
    with DefaultElasticsearchClientComponent
    with DefaultElasticsearchConfigurationComponent
    with DefaultEventBusServiceComponent
    with DefaultFeatureServiceComponent
    with DefaultFixturesServiceComponent
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
    with MakeAuthentication
    with MakeDataHandlerComponent
    with MakeSettingsComponent
    with MailJetConfigurationComponent
    with PersistentCrmUserServiceComponent
    with PersistentSequenceConfigurationComponent
    with PersistentTopIdeaCommentServiceComponent
    with PersistentTopIdeaServiceComponent
    with PostServiceComponent
    with ProposalCoordinatorComponent
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

  override val makeSettings: MakeSettings = mock[MakeSettings]
  when(makeSettings.defaultAdmin).thenReturn(DefaultAdmin("firstName", "admin@make.org", "password"))
  when(makeSettings.maxHistoryProposalsPerPage).thenReturn(10)
  when(makeSettings.resetTokenExpiresIn).thenReturn(1.day)
  when(makeSettings.resetTokenB2BExpiresIn).thenReturn(1.day)
  when(makeSettings.validationTokenExpiresIn).thenReturn(1.day)

  override val jobCoordinator: ActorRef[JobActor.Protocol.Command] = JobCoordinator(system, 1.minute)
  override lazy val proposalCoordinator: ActorRef[ProposalCommand] =
    ProposalCoordinator(system, sessionHistoryCoordinatorService, 1.second, idGenerator)
  override lazy val sessionHistoryCoordinator: ActorRef[SessionHistoryCommand] =
    SessionHistoryCoordinator(system, userHistoryCoordinator, idGenerator, makeSettings)
  override val spawnActorRef: ActorRef[SpawnProtocol.Command] =
    system.systemActorOf(SpawnProtocol(), "spawner")
  override lazy val userHistoryCoordinator: ActorRef[UserHistoryCommand] = UserHistoryCoordinator(system)

  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]

  override val persistentCrmUserService: PersistentCrmUserService = mock[PersistentCrmUserService]
  override val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    mock[PersistentSequenceConfigurationService]
  when(persistentSequenceConfigurationService.persist(any)).thenReturn(Future.successful(true))
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]
  when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(any)).thenAnswer { _: QuestionId =>
    Future.successful(sequenceConfiguration)
  }
  override val demographicsCardService: DemographicsCardService = mock[DemographicsCardService]
  when(demographicsCardService.getOrPickRandom(any, any, any)).thenAnswer {
    (cardId: Option[DemographicsCardId], token: Option[String], _: QuestionId) =>
      (cardId, token) match {
        case (Some(_), Some(_)) =>
          Future.successful(
            Some(DemographicsCardResponse(demographicsCard(DemographicsCardId("card-id")), token = "s3CUr3t0k3N"))
          )
        case _ => Future.successful(None)
      }
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
    SpecificSequenceConfiguration(SpecificSequenceConfigurationId(questionId.value))
  private def explorationSequence =
    ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId(questionId.value))
  private def sequenceConfiguration =
    SequenceConfiguration.default.copy(
      sequenceId = SequenceId(questionId.value),
      questionId = questionId,
      mainSequence = explorationSequence,
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
               .result(jobCoordinatorService.get(JobId.Reindex), 1.minute)
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
                          proposalService.generateProposalKeyHash(
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

  Feature("Start a sequence with dedicated params") {
    Scenario("standard behaviour") {
      whenReady(
        sequenceService.startNewSequence((), None, questionId, Nil, requestContext, None, None),
        Timeout(60.seconds)
      ) { result =>
        result.proposals.size should be > 0
        result.proposals.map(p => getScorer(getProposal(p)).zone).distinct.size should be > 1
        result.proposals.foreach(_.votes.foreach(_.hasVoted shouldBe false))
        result.demographics should not be defined
      }
    }

    Scenario(s"Controversy zone behaviour") {
      whenReady(
        sequenceService.startNewSequence(Zone.Controversy, None, questionId, Nil, requestContext, None, None),
        Timeout(60.seconds)
      ) { result =>
        result.proposals.size should be > 0
        result.proposals.foreach { p =>
          val proposal = getProposal(p)
          val scorer = getScorer(proposal)
          scorer.zone shouldBe Zone.Controversy
          scorer.pool(sequenceConfiguration, proposal.status) shouldBe SequencePool.Tested
          p.votes.foreach(_.hasVoted shouldBe false)
        }
        result.demographics should not be defined
      }
    }

    Scenario(s"Consensus zone behaviour") {
      val futureSequence = for {
        thresholds <- elasticsearchProposalAPI.computeTop20ConsensusThreshold(NonEmptyList(questionId, Nil))
        consensus = ConsensusParam(thresholds.get(questionId))
        results <- sequenceService.startNewSequence(consensus, None, questionId, Nil, requestContext, None, None)
      } yield results

      whenReady(futureSequence, Timeout(60.seconds)) { result =>
        result.proposals.size should be > 0
        result.proposals.foreach { p =>
          val proposal = getProposal(p)
          val scorer = getScorer(proposal)
          scorer.zone shouldBe Zone.Consensus
          scorer.pool(sequenceConfiguration, proposal.status) shouldBe SequencePool.Tested
          p.votes.foreach(_.hasVoted shouldBe false)
        }
        result.demographics should not be defined
      }
    }

    Scenario("keyword behaviour") {
      val futureSequenceKey = for {
        proposals <- elasticsearchProposalAPI.searchProposals(SearchQuery(limit = Some(10)))
        key = proposals.results.flatMap(_.keywords.map(_.key)).headOption.getOrElse(ProposalKeywordKey("undefined"))
        sequence <- sequenceService.startNewSequence(key, None, questionId, Nil, requestContext, None, None)
      } yield (sequence, key)
      whenReady(futureSequenceKey, Timeout(60.seconds)) {
        case (_, ProposalKeywordKey("undefined")) => succeed
        case (sequence, _) =>
          sequence.proposals.size should be > 0
          sequence.proposals.foreach(_.keywords.map(_.key) should contain(key))
          sequence.demographics should not be defined
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
  val port: Int = 15101
  val configuration: String =
    s"""
       |akka {
       |  cluster.seed-nodes = ["akka://SequenceServiceIT@localhost:$port"]
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
       |    port = $port
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
       |  security.secure-hash-salt = "salt-secure"
       |  security.secure-vote-salt = "vote-secure"
       |}
    """.stripMargin

  val fullConfiguration: Config =
    ConfigFactory
      .parseString(configuration)
      .withFallback(ConfigFactory.load("default-application.conf"))
      .resolve()

  val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "SequenceServiceIT", fullConfiguration)
}
