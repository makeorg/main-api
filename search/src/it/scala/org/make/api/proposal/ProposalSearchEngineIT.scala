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
import java.time.temporal.ChronoUnit
import java.util.UUID
import cats.data.NonEmptyList
import cats.syntax.list._
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import eu.timepit.refined.auto._
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.NonNegInt
import org.make.api.docker.SearchEngineIT
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.MakeUnitTest
import org.make.core.common.indexed.Sort
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed.Zone.{Consensus, Controversy, Limbo}
import org.make.core.proposal.indexed._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{UserId, UserType}
import org.make.core.{CirceFormatters, DateHelper, RequestContext, SlugHelper}
import org.make.core.DateHelper.zonedDateTimeOrder
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.util.concurrent.atomic.AtomicLong
import scala.collection.immutable.Seq
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.math.Ordering.Double.TotalOrdering
import org.make.api.technical.ActorSystemComponent

class ProposalSearchEngineIT
    extends MakeUnitTest
    with CirceFormatters
    with SearchEngineIT[ProposalId, IndexedProposal]
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent
    with ScalaCheckDrivenPropertyChecks {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30000

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.proposalAliasName).thenReturn(defaultElasticsearchProposalIndex)
  when(elasticsearchConfiguration.indexName).thenReturn(defaultElasticsearchProposalIndex)

  override val eSIndexName: String = defaultElasticsearchProposalIndex
  override val eSDocType: String = defaultElasticsearchProposalDocType
  override def docs: Seq[IndexedProposal] = proposals

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch(_.id)
  }

  val baseQuestion: IndexedProposalQuestion = IndexedProposalQuestion(
    questionId = QuestionId("question-id"),
    "slug",
    "title",
    "question",
    NonEmptyList.of(Country("FR")),
    Language("fr"),
    ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
    ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
    isOpen = false
  )
  val otherQuestion = baseQuestion.copy(questionId = QuestionId("other-questionId"))

  private val now = DateHelper.now()
  private val decrementer = new AtomicLong()
  private def newProposal = indexedProposal(
    id = ProposalId(UUID.randomUUID().toString),
    userId = UserId("user-id"),
    createdAt = now.minusDays(decrementer.incrementAndGet()),
    updatedAt = None,
    votes = Seq(
      IndexedVote
        .empty(VoteKey.Agree)
        .copy(qualifications = Seq(
          IndexedQualification.empty(QualificationKey.LikeIt),
          IndexedQualification.empty(QualificationKey.Doable),
          IndexedQualification.empty(QualificationKey.PlatitudeAgree)
        )
        ),
      IndexedVote
        .empty(key = VoteKey.Disagree)
        .copy(qualifications = Seq(
          IndexedQualification.empty(QualificationKey.NoWay),
          IndexedQualification.empty(QualificationKey.Impossible),
          IndexedQualification.empty(QualificationKey.PlatitudeDisagree)
        )
        ),
      IndexedVote
        .empty(key = VoteKey.Neutral)
        .copy(qualifications = Seq(
          IndexedQualification.empty(QualificationKey.DoNotUnderstand),
          IndexedQualification.empty(QualificationKey.NoOpinion),
          IndexedQualification.empty(QualificationKey.DoNotCare)
        )
        )
    ),
    status = ProposalStatus.Refused
  )
  private def newTag(label: String, display: Boolean = true) =
    IndexedTag(TagId(UUID.randomUUID().toString), label, display)

  val tagAlpha = newTag("alpha, with a comma")
  val tagBeta = newTag("beta")
  val tagGamma = newTag("gamma")
  val tagDelta = newTag("delta", false)

  val emptyContext: Option[IndexedContext] = Some(IndexedContext(RequestContext.empty))
  val frenchContext: Option[IndexedContext] =
    emptyContext.map(_.copy(country = Some(Country("FR")), language = Some(Language("fr"))))
  val italianContext: Option[IndexedContext] =
    emptyContext.map(_.copy(country = Some(Country("IT")), language = Some(Language("it"))))

  private val acceptedProposals: Seq[IndexedProposal] = Seq(
    indexedProposal(
      id = ProposalId("f4b02e75-8670-4bd0-a1aa-6d91c4de968a"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut que mon/ma député(e) fasse la promotion de la permaculture",
      createdAt = now.minusDays(49).minusHours(4),
      updatedAt = Some(now.minusDays(49).minusHours(4)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 123,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 105,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 59,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      toEnrich = true,
      author = IndexedAuthor(
        firstName = Some("Craig"),
        displayName = Some("Craig"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("92876"),
        age = Some(25),
        avatarUrl = Some("avatar.url"),
        anonymousParticipation = false,
        userType = UserType.UserTypeUser,
        profession = None
      ),
      tags = Seq(tagAlpha, tagBeta, tagGamma, tagDelta),
      selectedStakeTag = Some(tagGamma),
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.New,
      keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("culture"), "permaculture"))
    ).copy(
      scores = IndexedScores.empty.copy(topScore = IndexedScore(42, 60, 84), zone = Consensus),
      segmentScores =
        IndexedScores.empty.copy(topScore = IndexedScore(0, 54, 0), controversy = IndexedScore(0, 21, 0), zone = Limbo),
      context = italianContext,
      question = Some(baseQuestion)
    ),
    indexedProposal(
      id = ProposalId("9c468c22-1d1a-474b-9081-d79f1079f5e5"),
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content = "Il faut qu'il/elle interdise les élevages et cultures intensives",
      createdAt = now.minusDays(50),
      updatedAt = Some(now.minusDays(50)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 79,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 104,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 127,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      toEnrich = true,
      tags = Seq(tagAlpha, tagBeta, tagDelta),
      selectedStakeTag = Some(tagBeta),
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("culture"), "cultures"))
    ).copy(
      scores = IndexedScores.empty.copy(topScore = IndexedScore(54, 21, 0), zone = Controversy),
      segmentScores = IndexedScores.empty,
      context = frenchContext,
      question = Some(baseQuestion)
    ),
    indexedProposal(
      id = ProposalId("ed8d8b66-579a-48bd-9f61-b7f6cf679e95"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut qu'il/elle privilégie les petites exploitations agricoles aux fermes usines",
      createdAt = now.minusDays(48),
      updatedAt = Some(now.minusDays(48)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 56,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 18,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 53,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      toEnrich = true,
      ideaId = Some(IdeaId("idea-id-2")),
      tags = Seq(tagBeta),
      selectedStakeTag = Some(tagBeta),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      segment = Some("ubik")
    ).copy(
      scores = IndexedScores.empty
        .copy(topScore = IndexedScore(0, 12, 0), controversy = IndexedScore(0, 35, 0), zone = Consensus),
      segmentScores = IndexedScores.empty,
      context = italianContext,
      question = Some(otherQuestion)
    ),
    indexedProposal(
      id = ProposalId("c700b4c0-1b49-4373-a993-23c2437e857a"),
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content =
        "Il faut qu'il/elle protège notre agriculture locale et donne les moyens aux agriculteurs de vivre de leur métier de production",
      createdAt = now.minusDays(47),
      updatedAt = Some(now.minusDays(47)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 152,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 78,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 123,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      tags = Seq(tagBeta),
      selectedStakeTag = Some(tagDelta),
      ideaId = Some(IdeaId("idea-id-3")),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested
    ).copy(
      scores = IndexedScores.empty.copy(topScore = IndexedScore(16, 9.4, 0), zone = Consensus),
      segmentScores = IndexedScores.empty,
      context = frenchContext,
      question = Some(otherQuestion)
    ),
    indexedProposal(
      id = ProposalId("eac55aab-021e-495e-9664-bea941b8c51c"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "Il faut qu'il/elle favorise l'accès à l'alimentation issue de l'agriculture biologique",
      createdAt = now.minusDays(49).minusHours(8),
      updatedAt = Some(now.minusDays(49).minusHours(8)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 175,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 70,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 123,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested
    ).copy(
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = italianContext,
      question = Some(otherQuestion)
    ),
    indexedProposal(
      id = ProposalId("5725e8fc-54a1-4b77-9246-d1de60a245c5"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content =
        "Il faut qu'il/elle dissolve la SAFER et ainsi laisser les petits paysans s'installer, avec des petites exploitations",
      createdAt = now.minusDays(50).minusHours(1),
      updatedAt = Some(now.minusDays(50).minusHours(1)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 48,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 70,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 187,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested
    ).copy(
      scores = IndexedScores.empty.copy(zone = Consensus),
      segmentScores = IndexedScores.empty,
      context = None,
      question = Some(baseQuestion)
    ),
    indexedProposal(
      id = ProposalId("d38244bc-3d39-44a2-bfa9-a30158a297a3"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "C'è bisogno lui / lei deve sostenere e difendere l'agricoltura nel mio dipartimento",
      createdAt = now.minusDays(46),
      updatedAt = Some(now.minusDays(46)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 60,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 56,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 170,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested
    ).copy(
      scores = IndexedScores.empty.copy(topScore = IndexedScore(0, 80, 0), zone = Consensus),
      segmentScores = IndexedScores.empty,
      context = frenchContext,
      question = Some(baseQuestion.copy(countries = NonEmptyList.of(Country("IT"))))
    ),
    indexedProposal(
      id = ProposalId("ddba011d-5950-4237-bdf1-8bf25473f366"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "C'è bisogno lui / lei deve favorire i produttori locali per le mense e i pasti a casa.",
      createdAt = now.minusDays(44),
      updatedAt = Some(now.minusDays(44)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 95,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 32,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 35,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      operationId = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested
    ).copy(
      scores = IndexedScores.empty.copy(zone = Consensus),
      segmentScores = IndexedScores.empty,
      context = frenchContext,
      question = Some(baseQuestion.copy(isOpen = true))
    )
  )

  private val pendingProposals: Seq[IndexedProposal] = Seq(
    indexedProposal(
      id = ProposalId("7413c8dd-9b17-44be-afc8-fb2898b12773"),
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content =
        "Il faut qu'il/elle favorise l'agriculture qualitative plut\\u00f4t que l'agriculture intensive (plus de pesticides pour plus de rendements)",
      createdAt = now.minusDays(49).minusHours(1),
      updatedAt = Some(now.minusDays(49).minusHours(1)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 37,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 66,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 75,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      status = ProposalStatus.Pending,
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded
    ).copy(scores = IndexedScores.empty, segmentScores = IndexedScores.empty, context = frenchContext, question = None),
    indexedProposal(
      id = ProposalId("3bd7ae66-d2b4-42c2-96dd-46dbdb477797"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content =
        "Il faut qu'il/elle vote une loi pour obliger l'industrie pharmaceutique d'investir dans la recherche sur les maladies rares",
      createdAt = now.minusDays(45),
      updatedAt = Some(now.minusDays(45)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 67,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 42,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 22,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      status = ProposalStatus.Pending,
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      keywords = Seq(IndexedProposalKeyword(ProposalKeywordKey("maladie"), "maladies"))
    ).copy(
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = italianContext,
      question = None
    ),
    indexedProposal(
      id = ProposalId("bd44db77-3096-4e3b-b539-a4038307d85e"),
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content =
        "Il faut qu'il/elle propose d'interdire aux politiques l'utilisation du big data menant à faire des projets démagogiques",
      createdAt = now.minusDays(49).minusHours(2),
      updatedAt = Some(now.minusDays(49).minusHours(2)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 116,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 167,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 73,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      author = IndexedAuthor(
        firstName = None,
        displayName = Some("organisation"),
        organisationName = Some("organisation"),
        organisationSlug = Some("orga"),
        postalCode = Some("40734"),
        age = Some(23),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeOrganisation,
        profession = Some("Admin stuff")
      ),
      status = ProposalStatus.Pending,
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded
    ).copy(scores = IndexedScores.empty, segmentScores = IndexedScores.empty, context = frenchContext, question = None),
    indexedProposal(
      id = ProposalId("f2153c81-c031-41f0-8b02-c6ed556d62aa"),
      userId = UserId("ef418fad-2d2c-4f49-9b36-bf9d6f282aa2"),
      content =
        "Il faut qu'il/elle mette en avant la création de lieux de culture et d'échange, avec quelques petites subventions",
      createdAt = now.minusDays(49).minusHours(3),
      updatedAt = Some(now.minusDays(49).minusHours(3)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 86,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 165,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 96,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      author = IndexedAuthor(
        firstName = Some("Jennifer - Personality"),
        displayName = Some("Jennifer - Personality"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("81966"),
        age = Some(21),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypePersonality,
        profession = None
      ),
      status = ProposalStatus.Pending,
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded
    ).copy(
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = italianContext,
      question = None
    ),
    indexedProposal(
      id = ProposalId("13b16b9c-9293-4d33-9b82-415264820639"),
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content = "Il faut qu'il/elle défende un meilleur accès à la culture et à l'éducation pour tous.",
      createdAt = now.minusDays(49).minusHours(5),
      updatedAt = Some(now.minusDays(49).minusHours(5)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 170,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 33,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 64,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      status = ProposalStatus.Pending,
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded
    ).copy(scores = IndexedScores.empty, segmentScores = IndexedScores.empty, context = frenchContext, question = None),
    indexedProposal(
      id = ProposalId("b3198ad3-ff48-49f2-842c-2aefc3d0df5d"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut qu'il/elle pratique le mécennat et crée des aides pour les artistes, surtout les jeunes.",
      createdAt = now.minusDays(49).minusHours(6),
      updatedAt = Some(now.minusDays(49).minusHours(6)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 17,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 119,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 68,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      status = ProposalStatus.Pending,
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded
    ).copy(scores = IndexedScores.empty, segmentScores = IndexedScores.empty, context = frenchContext, question = None),
    indexedProposal(
      id = ProposalId("cf940085-010d-46de-8bfd-dee7e8adc8b6"),
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content =
        "C'è bisogno lui / lei deve difendere la Francofonia nel mondo combattendo contro l'egemonia dell'inglese",
      createdAt = now.minusDays(49).minusHours(7),
      updatedAt = Some(now.minusDays(49).minusHours(7)),
      votes = Seq(
        IndexedVote
          .empty(key = VoteKey.Agree)
          .copy(
            count = 124,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.LikeIt),
              IndexedQualification.empty(key = QualificationKey.Doable),
              IndexedQualification.empty(key = QualificationKey.PlatitudeAgree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Disagree)
          .copy(
            count = 74,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.NoWay),
              IndexedQualification.empty(key = QualificationKey.Impossible),
              IndexedQualification.empty(key = QualificationKey.PlatitudeDisagree)
            )
          ),
        IndexedVote
          .empty(key = VoteKey.Neutral)
          .copy(
            count = 56,
            qualifications = Seq(
              IndexedQualification.empty(key = QualificationKey.DoNotUnderstand),
              IndexedQualification.empty(key = QualificationKey.NoOpinion),
              IndexedQualification.empty(key = QualificationKey.DoNotCare)
            )
          )
      ),
      status = ProposalStatus.Pending,
      operationId = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded
    ).copy(scores = IndexedScores.empty, segmentScores = IndexedScores.empty, context = italianContext, question = None)
  )

  private def proposals: Seq[IndexedProposal] = acceptedProposals ++ pendingProposals

  Feature("get proposal by id") {
    val proposalId = proposals.head.id
    Scenario("should return a proposal") {
      whenReady(elasticsearchProposalAPI.findProposalById(proposalId), Timeout(3.seconds)) {
        case Some(proposal) =>
          proposal.id should equal(proposalId)
        case None => fail("proposal not found by id")
      }
    }
  }

  Feature("search proposals by content") {
    Given("searching by keywords")
    val query =
      SearchQuery(filters = Some(SearchFilters(content = Some(ContentSearchFilter(text = "Il faut qu'il/elle")))))

    Scenario("should return a list of proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
      }
    }
  }

  Feature("sort proposals by content") {
    val query =
      SearchQuery(
        filters = Some(
          SearchFilters(
            content = Some(ContentSearchFilter(text = "Il faut qu'il/elle")),
            status = Some(StatusSearchFilter(ProposalStatus.values))
          )
        ),
        sort = Some(Sort(Some("content.keyword"), None)),
        limit = Some(100)
      )

    Scenario("should return a sorted list of proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.results.map(_.content) should be(
          proposals.map(_.content).filter(_.startsWith("Il faut qu'il/elle")).sorted
        )
      }
    }
  }

  Feature("empty query returns accepted proposals only") {
    Given("searching without query")
    val query = SearchQuery()
    Scenario("should return a list of accepted proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(acceptedProposals.size)
      }
    }
  }

  Feature("search proposals by status") {
    Given("searching pending proposals")
    val query = SearchQuery(
      Some(
        SearchFilters(
          status = Some(StatusSearchFilter(Seq(ProposalStatus.Pending))),
          tags = None,
          labels = None,
          content = None,
          context = None
        )
      )
    )
    Scenario("should return a list of pending proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        info(result.results.map(_.status).mkString)
        result.total should be(pendingProposals.size)
      }
    }
  }

  Feature("search proposals by language and/or country") {
    Given("searching by language 'fr' or country 'IT'")
    val queryLanguage =
      SearchQuery(filters = Some(SearchFilters(language = Some(LanguageSearchFilter(language = Language("fr"))))))
    val queryCountry =
      SearchQuery(filters = Some(SearchFilters(country = Some(CountrySearchFilter(country = Country("IT"))))))

    Scenario("should return a list of french proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(queryLanguage), Timeout(3.seconds)) { result =>
        result.total should be(acceptedProposals.count(_.question.map(_.language).contains(Language("fr"))))
      }
    }

    Scenario("should return a list of proposals from Italy") {
      whenReady(elasticsearchProposalAPI.searchProposals(queryCountry), Timeout(3.seconds)) { result =>
        result.total should be(
          acceptedProposals.count(_.question.toList.flatMap(_.countries.toList).contains(Country("IT")))
        )
      }
    }
  }

  Feature("search proposals by top score") {
    val queryTopScore =
      SearchQuery(
        filters = None,
        sort =
          Some(Sort(field = Some(ProposalElasticsearchFieldName.scoreLowerBound.field), mode = Some(SortOrder.Desc)))
      )

    Scenario("should return a list of proposals sorted by top score") {
      whenReady(elasticsearchProposalAPI.searchProposals(queryTopScore), Timeout(3.seconds)) { result =>
        result.total should be(acceptedProposals.size)
        result.results.map(_.scores.topScore.lowerBound) should be(
          acceptedProposals.map(_.scores.topScore.lowerBound).sorted.reverse
        )
      }
    }
  }

  Feature("search proposals by slug") {
    Scenario("searching a non-existing slug") {
      val query = SearchQuery(Some(SearchFilters(slug = Some(SlugSearchFilter("something-I-dreamt")))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(0)
      }
    }

    Scenario("searching an existing slug") {
      val slug = SlugHelper(proposals.head.content)
      val query = SearchQuery(Some(SearchFilters(slug = Some(SlugSearchFilter(slug)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(1)
        result.results.head.slug should be(slug)
      }
    }

    Scenario("search proposal by user") {
      val userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5")
      val userId2 = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a")
      val query = SearchQuery(
        Some(
          SearchFilters(
            status = Some(StatusSearchFilter(Seq(ProposalStatus.Pending, ProposalStatus.Accepted))),
            users = Some(UserSearchFilter(Seq(userId, userId2)))
          )
        )
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(7)
        result.results.map(_.userId).toSet should be(
          Set(UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"), UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"))
        )
      }
    }

    Scenario("search proposals by created date") {

      val searchDate: ZonedDateTime = now.minusDays(47)
      val queryBefore: SearchQuery = SearchQuery(
        Some(SearchFilters(createdAt = Some(CreatedAtSearchFilter(before = Some(searchDate), after = None))))
      )
      val queryAfter: SearchQuery =
        SearchQuery(Some(SearchFilters(createdAt = Some(CreatedAtSearchFilter(None, after = Some(searchDate))))))
      val queryBeforeAfter: SearchQuery =
        SearchQuery(
          Some(
            SearchFilters(createdAt = Some(
              CreatedAtSearchFilter(
                before = Some(searchDate.plus(3, ChronoUnit.DAYS)),
                after = Some(searchDate.minus(1, ChronoUnit.DAYS))
              )
            )
            )
          )
        )

      whenReady(elasticsearchProposalAPI.searchProposals(queryBefore), Timeout(3.seconds)) { result =>
        result.total should be(5)
      }
      whenReady(elasticsearchProposalAPI.searchProposals(queryAfter), Timeout(3.seconds)) { result =>
        result.total should be(3)
      }
      whenReady(elasticsearchProposalAPI.searchProposals(queryBeforeAfter), Timeout(3.seconds)) { result =>
        result.total should be(2)
      }

    }

  }

  Feature("excludes proposals from search") {
    Scenario("proposals excluded should not be in the result") {
      forAll { count: NonNegInt =>
        val proposalsToExclude: Seq[ProposalId] = acceptedProposals.take(count).map(_.id)
        val query =
          SearchQuery(excludes = Some(SearchFilters(proposal = Some(ProposalSearchFilter(proposalsToExclude)))))

        whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
          result.total should be(acceptedProposals.size - proposalsToExclude.size)
          (result.results.map(_.id) should contain).noElementsOf(proposalsToExclude)
        }
      }
    }
  }

  Feature("saving new proposal") {
    Scenario("should return distinct new") {
      val proposal1 = newProposal
      val proposal2 = newProposal
      whenReady(
        elasticsearchProposalAPI.indexProposals(Seq(proposal1, proposal1, proposal1, proposal2)),
        Timeout(3.seconds)
      ) { result =>
        result.size should be(2)
        result.exists(_.id == proposal1.id) should be(true)
        result.exists(_.id == proposal2.id) should be(true)
      }
    }
  }

  Feature("search proposals by toEnrich") {
    Scenario("should return a list of proposals") {
      Given("a boolean set to true")
      val query =
        SearchQuery(filters = Some(SearchFilters(toEnrich = Some(ToEnrichSearchFilter(toEnrich = true)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be < 6L
        result.total should be > 0L
      }
    }

    Scenario("should not return proposals with no tags") {
      Given("a boolean set to false")
      val query =
        SearchQuery(filters = Some(SearchFilters(toEnrich = Some(ToEnrichSearchFilter(toEnrich = false)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
        result.results.forall(!_.toEnrich) should be(true)
      }
    }
  }

  Feature("search proposals by minVotes") {
    Scenario("should return a list of proposals") {
      Given("minimum vote number")
      val query =
        SearchQuery(filters = Some(SearchFilters(minVotesCount = Some(MinVotesCountSearchFilter(42)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
      }
    }
  }

  Feature("search proposals by minScore") {
    Scenario("should return a list of proposals") {
      Given("minimum vote number")
      val query =
        SearchQuery(filters = Some(SearchFilters(minScore = Some(MinScoreSearchFilter(42)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(1L)
      }
    }
  }

  Feature("search proposals by opened question") {
    Scenario("should return one proposals") {
      val queryTrue =
        SearchQuery(filters = Some(SearchFilters(questionIsOpen = Some(QuestionIsOpenSearchFilter(true)))))
      val queryFalse =
        SearchQuery(filters = Some(SearchFilters(questionIsOpen = Some(QuestionIsOpenSearchFilter(false)))))

      whenReady(elasticsearchProposalAPI.searchProposals(queryTrue), Timeout(3.seconds)) { result =>
        result.total should be(1L)
      }
      whenReady(elasticsearchProposalAPI.searchProposals(queryFalse), Timeout(3.seconds)) { result =>
        result.total should be(7L)
      }
    }
  }

  Feature("search proposals by segment") {
    Scenario("search for segment ubik") {
      val query = SearchQuery(filters = Some(SearchFilters(segment = Some(SegmentSearchFilter("ubik")))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(1)
        results.results.foreach(_.segment should contain("ubik"))
      }
    }
  }

  Feature("segment-first algorithm") {
    Scenario("segment-first algorithm") {
      val segment = "ubik"
      val query = SearchQuery(sortAlgorithm = Some(SegmentFirstAlgorithm(segment)), limit = Some(10))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        val proposals = results.results

        val segmentProposals = proposals.takeWhile(_.segment.contains(segment))
        segmentProposals.size should be >= 1
        segmentProposals.foreach(_.segment should contain(segment))
        segmentProposals.sortBy(_.createdAt.toString).reverse should be(segmentProposals)

        val nonSegmentProposals = proposals.dropWhile(_.segment.contains(segment))
        nonSegmentProposals.size should be >= 1
        nonSegmentProposals.foreach(_.segment should not contain (segment))
        nonSegmentProposals.sortBy(_.createdAt.toString).reverse.map(_.id) should be(nonSegmentProposals.map(_.id))
      }
    }
  }

  Feature("search proposals by sequence segment pool") {
    Scenario("search for pool new") {
      val query = SearchQuery(filters =
        Some(SearchFilters(sequenceSegmentPool = Some(SequencePoolSearchFilter(SequencePool.New))))
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(1)
        results.results.foreach(_.sequenceSegmentPool should be(SequencePool.New))
        results.results.foreach(_.id.value should be("f4b02e75-8670-4bd0-a1aa-6d91c4de968a"))
      }
    }
  }

  Feature("search proposals by author is organisation") {
    Scenario("search for author organisation") {
      val query = SearchQuery(filters = Some(
        SearchFilters(
          userTypes = Some(UserTypesSearchFilter(Seq(UserType.UserTypeOrganisation))),
          status = Some(StatusSearchFilter(ProposalStatus.values))
        )
      )
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(1)
        results.results.foreach(_.author.userType should be(UserType.UserTypeOrganisation))
        results.results.foreach(_.id.value should be("bd44db77-3096-4e3b-b539-a4038307d85e"))
        results.results.foreach(_.author.profession should be(Some("Admin stuff")))
      }
    }
  }

  Feature("get popular tags") {
    Scenario("get tags for base question") {
      whenReady(elasticsearchProposalAPI.getPopularTagsByProposal(baseQuestion.questionId, 10), Timeout(10.seconds)) {
        results =>
          results.size should be(3)
          results.find(_.tagId == tagAlpha.tagId).foreach(_.proposalCount should be(2))
          results.find(_.tagId == tagBeta.tagId).foreach(_.proposalCount should be(2))
          results.find(_.tagId == tagGamma.tagId).foreach(_.proposalCount should be(1))
          results.exists(_.tagId == tagDelta.tagId) shouldBe false
      }
    }

    Scenario("get tags for inexistent question") {
      whenReady(elasticsearchProposalAPI.getPopularTagsByProposal(QuestionId("fake"), 10), Timeout(10.seconds)) {
        results =>
          results.size should be(0)
      }
    }
  }

  Feature("get top proposals") {
    Scenario("get top proposals by idea for base question") {
      whenReady(
        elasticsearchProposalAPI.getTopProposals(otherQuestion.questionId, 10, ProposalElasticsearchFieldName.ideaId),
        Timeout(10.seconds)
      ) { results =>
        results.take(3).map(_.scores.topScore.lowerBound) should be(Seq(12.0, 9.4))
        results.take(3).flatMap(_.ideaId).map(_.value) should be(Seq("idea-id-2", "idea-id-3"))
      }
    }

    Scenario("get top proposals by stake tag") {
      whenReady(
        elasticsearchProposalAPI
          .getTopProposals(otherQuestion.questionId, 10, ProposalElasticsearchFieldName.selectedStakeTagId),
        Timeout(10.seconds)
      ) { results =>
        results.take(3).map(_.scores.topScore.lowerBound) should be(Seq(12.0, 9.4))
        results.take(3).flatMap(_.selectedStakeTag).map(_.label) should be(Seq("beta", "delta"))
      }
    }
  }

  Feature("count proposals by idea") {
    Scenario("no ideas") {
      whenReady(elasticsearchProposalAPI.countProposalsByIdea(Seq.empty), Timeout(3.seconds)) { results =>
        results.size shouldBe 0
      }
    }

    Scenario("some ideas") {
      val ideaIdOne = IdeaId("idea-id")
      val ideaIdTwo = IdeaId("idea-id-2")
      whenReady(elasticsearchProposalAPI.countProposalsByIdea(Seq(ideaIdOne, ideaIdTwo)), Timeout(3.seconds)) {
        results =>
          results.get(ideaIdOne).isDefined shouldBe true
          results(ideaIdOne) shouldBe 2
          results.get(ideaIdTwo).isDefined shouldBe true
          results.get(IdeaId("idea-id-3")).isDefined shouldBe false
      }
    }
  }

  Feature("search proposals by userType") {
    Scenario("search for author organisation and personality") {
      val query = SearchQuery(filters = Some(
        SearchFilters(
          userTypes = Some(UserTypesSearchFilter(Seq(UserType.UserTypeOrganisation, UserType.UserTypePersonality))),
          status = Some(StatusSearchFilter(ProposalStatus.values))
        )
      )
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(2)
        results.results.exists(_.author.userType == UserType.UserTypeOrganisation) shouldBe true
        results.results.exists(_.author.userType == UserType.UserTypePersonality) shouldBe true
      }
    }
  }

  Feature("get random proposals by top idea") {
    Scenario("get random proposals by top idea") {
      whenReady(
        elasticsearchProposalAPI
          .getRandomProposalsByIdeaWithAvatar(Seq(IdeaId("idea-id"), IdeaId("idea-id-2"), IdeaId("idea-id-3")), 42),
        Timeout(10.seconds)
      ) { result =>
        result(IdeaId("idea-id")).proposalsCount should be(2)
        result(IdeaId("idea-id")).avatars should contain("avatar.url")
        result(IdeaId("idea-id-2")).proposalsCount should be(1)
        result(IdeaId("idea-id-3")).proposalsCount should be(1)
      }
    }

    Scenario("top ideas are not defined yet") {
      whenReady(elasticsearchProposalAPI.getRandomProposalsByIdeaWithAvatar(Seq.empty, 42), Timeout(10.seconds)) {
        result =>
          result should be(Map.empty)
      }
    }
  }

  Feature("search by zone / segment zone") {
    Scenario("search by zone") {
      val query = SearchQuery(filters = Some(SearchFilters(zone = Some(ZoneSearchFilter(Consensus)))))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { result =>
        result.results.size should be >= 1
        result.results.foreach { proposal =>
          proposal.scores.zone should be(Consensus)
        }
      }
    }

    Scenario("search by segment zone") {
      val query = SearchQuery(filters = Some(SearchFilters(segmentZone = Some(ZoneSearchFilter(Limbo)))))
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { result =>
        result.results.size should be >= 1
        result.results.foreach { proposal =>
          proposal.segmentScores.zone should be(Limbo)
        }
      }
    }
  }

  Feature("search proposals by min score lower bound") {
    Scenario("should return a list of proposals") {
      val query =
        SearchQuery(filters = Some(SearchFilters(minScoreLowerBound = Some(MinScoreLowerBoundSearchFilter(21)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(3L)
      }
    }
  }

  Feature("search proposals by keyword") {
    Scenario("should return a list of proposals") {
      val query =
        SearchQuery(filters =
          Some(SearchFilters(keywords = Some(KeywordsSearchFilter(Seq(ProposalKeywordKey("culture"))))))
        )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(2L)
      }
    }
  }

  Feature("get featured proposals") {
    Scenario("should be deduplicated") {
      whenReady(elasticsearchProposalAPI.getFeaturedProposals(SearchQuery(limit = Some(3))), Timeout(10.seconds)) {
        results =>
          val proposals = results.results
          val expected = acceptedProposals.toList
            .groupByNel(_.userId)
            .map(_._2.sortBy(_.createdAt).last)
            .toList
            .sortBy(_.createdAt)
            .reverse
            .take(3)
          proposals shouldBe expected
      }
    }
  }

  Feature("compute top 20 consensus threshold") {
    Scenario("should return a list of proposals") {
      whenReady(
        elasticsearchProposalAPI.computeTop20ConsensusThreshold(
          NonEmptyList(baseQuestion.questionId, List(otherQuestion.questionId, QuestionId("fake")))
        ),
        Timeout(10.seconds)
      ) { thresholds =>
        thresholds.size should be(2)
        thresholds.get(baseQuestion.questionId) should be(Some(74d))
        thresholds.get(otherQuestion.questionId) should be(Some(12d))
      }
    }
  }

}
