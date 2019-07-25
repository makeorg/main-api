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

package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.MakeApiTestBase
import org.make.api.technical.auth.MakeAuthentication
import org.make.api.technical.elasticsearch.{
  ElasticsearchClient,
  ElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.core.idea.IdeaId
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.UserId
import org.mockito.Mockito

import scala.collection.immutable.Seq

class ProposalSearchEngineTest
    extends MakeApiTestBase
    with ElasticsearchConfigurationComponent
    with ElasticsearchClientComponent
    with DefaultProposalSearchEngineComponent
    with MakeAuthentication {

  override val elasticsearchClient: ElasticsearchClient = mock[ElasticsearchClient]
  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn("localhost:9200")
  Mockito.when(elasticsearchConfiguration.proposalAliasName).thenReturn("fakeAliasName")
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn("fakeIndexName")

  def proposal(nbAgree: Int, nbDisagree: Int, nbNeutral: Int): IndexedProposal = IndexedProposal(
    id = ProposalId("99999999-9999-9999-9999-999999999999"),
    country = Country("FR"),
    language = Language("fr"),
    userId = UserId("99999999-9999-9999-9999-999999999999"),
    content = "Il faut faire une proposition",
    slug = "il-faut-faire-une-proposition",
    createdAt = ZonedDateTime.now,
    updatedAt = Some(ZonedDateTime.now),
    votes = Seq(
      IndexedVote(
        key = VoteKey.Agree,
        count = nbAgree,
        qualifications = Seq(
          IndexedQualification(key = QualificationKey.LikeIt),
          IndexedQualification(key = QualificationKey.Doable),
          IndexedQualification(key = QualificationKey.PlatitudeAgree)
        )
      ),
      IndexedVote(
        key = VoteKey.Disagree,
        count = nbDisagree,
        qualifications = Seq(
          IndexedQualification(key = QualificationKey.NoWay),
          IndexedQualification(key = QualificationKey.Impossible),
          IndexedQualification(key = QualificationKey.PlatitudeDisagree)
        )
      ),
      IndexedVote(
        key = VoteKey.Neutral,
        count = nbNeutral,
        qualifications = Seq(
          IndexedQualification(key = QualificationKey.DoNotUnderstand),
          IndexedQualification(key = QualificationKey.NoOpinion),
          IndexedQualification(key = QualificationKey.DoNotCare)
        )
      )
    ),
    votesCount = 3,
    votesVerifiedCount = 3,
    toEnrich = false,
    scores = IndexedScores.empty,
    context =
      Some(Context(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)),
    trending = None,
    labels = Seq.empty,
    author = Author(
      firstName = Some("Boogie"),
      organisationName = None,
      organisationSlug = None,
      postalCode = Some("11111"),
      age = Some(42),
      avatarUrl = None
    ),
    organisations = Seq.empty,
    themeId = Some(ThemeId("foo-theme")),
    tags = Seq.empty,
    status = ProposalStatus.Accepted,
    ideaId = Some(IdeaId("idea-id")),
    operationId = None,
    question = None,
    sequencePool = SequencePool.New,
    initialProposal = false,
    refusalReason = None,
    operationKind = None
  )

  val normalProposal: IndexedProposal = proposal(42, 1, 3)
  val popularProposal: IndexedProposal = proposal(84, 6, 10)
  val controversialProposal: IndexedProposal = proposal(42, 54, 4)

  feature("define trending mode of a proposal") {
    scenario("normal proposal") {
      elasticsearchProposalAPI.proposalTrendingMode(normalProposal) should be(None)
    }

    scenario("popular proposal") {
      elasticsearchProposalAPI.proposalTrendingMode(popularProposal) should be(Some("popular"))
    }

    scenario("controversial proposal") {
      elasticsearchProposalAPI.proposalTrendingMode(controversialProposal) should be(Some("controversial"))
    }
  }
}
