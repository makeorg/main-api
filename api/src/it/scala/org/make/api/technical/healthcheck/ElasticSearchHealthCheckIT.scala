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

import com.sksamuel.elastic4s.ElasticImplicits.RichString
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticApi, IndexAndType}
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.syntax._
import org.make.api.docker.DockerElasticsearchService
import org.make.api.proposal.ProposalSearchEngine
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  DefaultElasticsearchConfigurationComponent,
  RichHttpClient
}
import org.make.api.technical.healthcheck.HealthCheck.Status
import org.make.api.{ConfigComponent, ItMakeTest, TestUtilsIT}
import org.make.core.idea.IdeaId
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.make.core.{CirceFormatters, DateFormatters, RequestContext}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZonedDateTime
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

class ElasticSearchHealthCheckIT
    extends ItMakeTest
    with CirceFormatters
    with DefaultElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ConfigComponent
    with DockerElasticsearchService
    with TestUtilsIT {

  override val config: Config = ElasticSearchHealthCheckIT.configuration

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch()
  }

  val proposal: IndexedProposal =
    indexedProposal(
      id = ProposalId("f4b02e75-8670-4bd0-a1aa-6d91c4de968a"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut que mon/ma député(e) fasse la promotion de la permaculture",
      createdAt = ZonedDateTime.from(DateFormatters.default.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(DateFormatters.default.parse("2017-06-02T01:01:01.123Z"))),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 123,
            qualifications = Seq(
              IndexedQualification.empty(QualificationKey.LikeIt),
              IndexedQualification.empty(QualificationKey.Doable),
              IndexedQualification.empty(QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 105,
            qualifications = Seq(
              IndexedQualification.empty(QualificationKey.NoWay),
              IndexedQualification.empty(QualificationKey.Impossible),
              IndexedQualification.empty(QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 59,
            qualifications = Seq(
              IndexedQualification.empty(QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(QualificationKey.NoOpinion),
              IndexedQualification.empty(QualificationKey.DoNotCare)
            )
          )
      ),
      requestContext = Some(RequestContext.empty.copy(country = Some(Country("FR")), language = Some(Language("fr")))),
      ideaId = Some(IdeaId("idea-id"))
    )

  private def initializeElasticsearch(): Unit = {
    Await.result(elasticsearchClient.initialize(), 30.seconds)

    val proposalAlias: IndexAndType =
      elasticsearchConfiguration.proposalAliasName / ProposalSearchEngine.proposalIndexName

    val insertFutures =
      elasticsearchClient.client.executeAsFuture(ElasticApi.indexInto(proposalAlias).doc(proposal.asJson.toString))

    Await.result(insertFutures, 150.seconds)

    Thread.sleep(3000)

  }

  override val elasticsearchExposedPort: Int = ElasticSearchHealthCheckIT.elasticsearchExposedPort

  implicit val ctx: ExecutionContext = global

  Feature("Check Elasticsearch status") {
    Scenario("count proposals") {
      val hc = new ElasticsearchHealthCheck(elasticsearchConfiguration, elasticsearchClient)

      whenReady(hc.healthCheck(), Timeout(30.seconds)) { res =>
        res should be(Status.OK)
      }
    }
  }
}

object ElasticSearchHealthCheckIT {
  val elasticsearchExposedPort = 30004

  // This configuration cannot be dynamic, port values _must_ match reality
  val configuration: Config =
    ConfigFactory.parseString(s"""
       |make-api.elasticSearch = {
       |  connection-string = "localhost:$elasticsearchExposedPort"
       |  index-name = "make"
       |  idea-alias-name = "idea"
       |  organisation-alias-name = "organisation"
       |  proposal-alias-name = "proposal"
       |  operation-of-question-alias-name = "operation-of-question"
       |  post-alias-name = "post"
       |  buffer-size = 100
       |  bulk-size = 100
       |}
    """.stripMargin)
}
