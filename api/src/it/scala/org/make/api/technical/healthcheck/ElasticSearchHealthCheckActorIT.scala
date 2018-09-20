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

package org.make.api.technical.healthcheck
import java.time.ZonedDateTime

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.sksamuel.elastic4s.ElasticImplicits.RichString
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticApi, IndexAndType}
import com.typesafe.config.ConfigFactory
import io.circe.syntax._
import org.make.api.ItMakeTest
import org.make.api.docker.DockerElasticsearchService
import org.make.api.proposal.ProposalSearchEngine
import org.make.api.technical.TimeSettings
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, RichHttpClient}
import org.make.api.technical.healthcheck.HealthCheckCommands.CheckStatus
import org.make.core.CirceFormatters
import org.make.core.idea.IdeaId
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.UserId

import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

class ElasticSearchHealthCheckActorIT
    extends TestKit(ElasticSearchHealthCheckActorIT.actorSystem)
    with ImplicitSender
    with ItMakeTest
    with CirceFormatters
    with DockerElasticsearchService {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
    initializeElasticsearch()
  }

  val elasticsearchConfiguration = new ElasticsearchConfiguration(
    system.settings.config.getConfig("make-api.elasticSearch")
  )

  val proposal =
    IndexedProposal(
      id = ProposalId("f4b02e75-8670-4bd0-a1aa-6d91c4de968a"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut que mon/ma député(e) fasse la promotion de la permaculture",
      slug = "il-faut-que-mon-ma-depute-fasse-la-promotion-de-la-permaculture",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votes = Seq(
        IndexedVote(
          key = VoteKey.Agree,
          count = 123,
          qualifications = Seq(
            IndexedQualification(key = QualificationKey.LikeIt),
            IndexedQualification(key = QualificationKey.Doable),
            IndexedQualification(key = QualificationKey.PlatitudeAgree)
          )
        ),
        IndexedVote(
          key = VoteKey.Disagree,
          count = 105,
          qualifications = Seq(
            IndexedQualification(key = QualificationKey.NoWay),
            IndexedQualification(key = QualificationKey.Impossible),
            IndexedQualification(key = QualificationKey.PlatitudeDisagree)
          )
        ),
        IndexedVote(
          key = VoteKey.Neutral,
          count = 59,
          qualifications = Seq(
            IndexedQualification(key = QualificationKey.DoNotUnderstand),
            IndexedQualification(key = QualificationKey.NoOpinion),
            IndexedQualification(key = QualificationKey.DoNotCare)
          )
        )
      ),
      scores = IndexedScores.empty,
      context = Some(Context(source = None, operation = None, location = None, question = None)),
      trending = None,
      labels = Seq(),
      author = Author(
        firstName = Some("Craig"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("92876"),
        age = Some(25),
        avatarUrl = None
      ),
      organisations = Seq.empty,
      themeId = Some(ThemeId("foo-theme")),
      tags = Seq(),
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      questionId = None
    )

  private def initializeElasticsearch(): Unit = {
    Await.result(elasticsearchConfiguration.initialize(), 5.seconds)

    val proposalAlias: IndexAndType =
      elasticsearchConfiguration.proposalAliasName / ProposalSearchEngine.proposalIndexName

    val insertFutures = elasticsearchConfiguration.client.executeAsFuture(
      ElasticApi.indexInto(proposalAlias).doc(proposal.asJson.toString)
    )

    Await.result(insertFutures, 150.seconds)

    Thread.sleep(3000)

  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  override val elasticsearchExposedPort: Int = ElasticSearchHealthCheckActorIT.elasticsearchExposedPort

  implicit val timeout: Timeout = TimeSettings.defaultTimeout
  feature("Check Elasticsearch status") {
    scenario("count proposals") {
      Given("an elasticsesarch health check actor")
      val actorSystem = system
      val healthCheckExecutionContext = ExecutionContext.Implicits.global
      val healthCheckElasticsearch: ActorRef = actorSystem.actorOf(
        ElasticsearchHealthCheckActor.props(healthCheckExecutionContext),
        ElasticsearchHealthCheckActor.name
      )

      When("I send a message to check the status of elasticsearch")
      healthCheckElasticsearch ! CheckStatus
      Then("I get the status")
      val msg: HealthCheckResponse = expectMsgType[HealthCheckResponse](timeout.duration)
      And("status is \"OK\"")
      msg should be(HealthCheckSuccess("elasticsearch", "OK"))
    }
  }
}

object ElasticSearchHealthCheckActorIT {
  val elasticsearchExposedPort = 30004

  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: String =
    s"""
       |make-api.elasticSearch = {
       |  connection-string = "localhost:$elasticsearchExposedPort"
       |  index-name = "make"
       |  idea-alias-name = "idea"
       |  organisation-alias-name = "organisation"
       |  proposal-alias-name = "proposal"
       |  sequence-alias-name = "sequence"
       |  buffer-size = 100
       |  bulk-size = 100
       |}
    """.stripMargin

  val actorSystem = ActorSystem("ElasticSearchHealthCheckActorIT", ConfigFactory.parseString(configuration))
}