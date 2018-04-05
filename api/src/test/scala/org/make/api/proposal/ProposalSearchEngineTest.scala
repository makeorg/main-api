package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.core.idea.IdeaId
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.proposal.indexed._
import org.make.core.reference.ThemeId
import org.make.core.user.UserId
import org.mockito.Mockito

import scala.collection.immutable.Seq

class ProposalSearchEngineTest
    extends MakeApiTestUtils
    with MakeSettingsComponent
    with IdGeneratorComponent
    with ElasticsearchConfigurationComponent
    with DefaultProposalSearchEngineComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  override val elasticsearchConfiguration: ElasticsearchConfiguration = mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn("localhost:9200")
  Mockito.when(elasticsearchConfiguration.aliasName).thenReturn("fakeAliasName")
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn("fakeIndexName")

  def proposal(nbAgree: Int, nbDisagree: Int, nbNeutral: Int): IndexedProposal = IndexedProposal(
    id = ProposalId("99999999-9999-9999-9999-999999999999"),
    country = "FR",
    language = "fr",
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
    context = Some(Context(source = None, operation = None, location = None, question = None)),
    trending = None,
    labels = Seq.empty,
    author = Author(firstName = Some("Boogie"), postalCode = Some("11111"), age = Some(42)),
    themeId = Some(ThemeId("foo-theme")),
    tags = Seq.empty,
    status = ProposalStatus.Accepted,
    ideaId = Some(IdeaId("idea-id")),
    operationId = None
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
