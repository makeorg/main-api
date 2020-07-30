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

import org.make.api.MakeApiTestBase
import org.make.api.technical.auth.MakeAuthentication
import org.make.api.technical.elasticsearch.{
  ElasticsearchClient,
  ElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.core.DateHelper
import org.make.core.idea.IdeaId
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language, Locale}
import org.make.core.user.{UserId, UserType}

import scala.collection.immutable.Seq

class ProposalSearchEngineTest
    extends MakeApiTestBase
    with ElasticsearchConfigurationComponent
    with ElasticsearchClientComponent
    with DefaultProposalSearchEngineComponent
    with MakeAuthentication {

  override val elasticsearchClient: ElasticsearchClient = mock[ElasticsearchClient]
  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn("localhost:9200")
  when(elasticsearchConfiguration.proposalAliasName).thenReturn("fakeAliasName")
  when(elasticsearchConfiguration.indexName).thenReturn("fakeIndexName")

  def proposal(nbAgree: Int, nbDisagree: Int, nbNeutral: Int): IndexedProposal = IndexedProposal(
    id = ProposalId("99999999-9999-9999-9999-999999999999"),
    userId = UserId("99999999-9999-9999-9999-999999999999"),
    content = "Il faut faire une proposition",
    slug = "il-faut-faire-une-proposition",
    createdAt = DateHelper.now(),
    updatedAt = Some(DateHelper.now()),
    votes = Seq(
      IndexedVote
        .empty(key = VoteKey.Agree)
        .copy(
          count = nbAgree,
          qualifications = Seq(
            IndexedQualification.empty(key = QualificationKey.LikeIt),
            IndexedQualification.empty(key = QualificationKey.Doable),
            IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
          )
        ),
      IndexedVote
        .empty(key = VoteKey.Disagree)
        .copy(
          count = nbDisagree,
          qualifications = Seq(
            IndexedQualification.empty(key = QualificationKey.NoWay),
            IndexedQualification.empty(key = QualificationKey.Impossible),
            IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
          )
        ),
      IndexedVote
        .empty(key = VoteKey.Neutral)
        .copy(
          count = nbNeutral,
          qualifications = Seq(
            IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
            IndexedQualification.empty(key = QualificationKey.NoOpinion),
            IndexedQualification.empty(key = QualificationKey.DoNotCare)
          )
        )
    ),
    votesCount = 3,
    votesVerifiedCount = 3,
    votesSequenceCount = 3,
    votesSegmentCount = 3,
    toEnrich = false,
    scores = IndexedScores.empty,
    segmentScores = IndexedScores.empty,
    context = Some(
      IndexedContext(
        source = None,
        operation = None,
        location = None,
        question = None,
        country = Some(Country("FR")),
        locale = Some(Locale(Language("fr"), Country("FR"))),
        getParameters = Seq.empty
      )
    ),
    trending = None,
    labels = Seq.empty,
    author = IndexedAuthor(
      firstName = Some("Boogie"),
      displayName = Some("Boogie"),
      organisationName = None,
      organisationSlug = None,
      postalCode = Some("11111"),
      age = Some(42),
      avatarUrl = None,
      anonymousParticipation = false,
      userType = UserType.UserTypeUser
    ),
    organisations = Seq.empty,
    tags = Seq.empty,
    selectedStakeTag = None,
    status = ProposalStatus.Accepted,
    ideaId = Some(IdeaId("idea-id")),
    operationId = None,
    question = None,
    sequencePool = SequencePool.New,
    sequenceSegmentPool = SequencePool.New,
    initialProposal = false,
    refusalReason = None,
    operationKind = None,
    segment = None
  )

  val normalProposal: IndexedProposal = proposal(42, 1, 3)
  val popularProposal: IndexedProposal = proposal(84, 6, 10)
  val controversialProposal: IndexedProposal = proposal(42, 54, 4)

  Feature("define trending mode of a proposal") {
    Scenario("normal proposal") {
      elasticsearchProposalAPI.proposalTrendingMode(normalProposal) should be(None)
    }

    Scenario("popular proposal") {
      elasticsearchProposalAPI.proposalTrendingMode(popularProposal) should be(Some("popular"))
    }

    Scenario("controversial proposal") {
      elasticsearchProposalAPI.proposalTrendingMode(controversialProposal) should be(Some("controversial"))
    }
  }
}
