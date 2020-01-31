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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source => AkkaSource}
import io.circe.syntax._
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.api.docker.DockerElasticsearchService
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.core.idea.{CountrySearchFilter, IdeaId, LanguageSearchFilter}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.{UserId, UserType}
import org.make.core.{CirceFormatters, DateHelper}
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

class ProposalSearchEngineIT
    extends ItMakeTest
    with CirceFormatters
    with DockerElasticsearchService
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchExposedPort: Int = 30000

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  Mockito.when(elasticsearchConfiguration.proposalAliasName).thenReturn(defaultElasticsearchProposalIndex)
  Mockito.when(elasticsearchConfiguration.indexName).thenReturn(defaultElasticsearchProposalIndex)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch()
  }

  private def initializeElasticsearch(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem()
    val elasticsearchEndpoint = s"http://localhost:$elasticsearchExposedPort"
    val proposalMapping =
      Source.fromResource("elasticsearch-mappings/proposal.json")(Codec.UTF8).getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$defaultElasticsearchProposalIndex",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposalMapping)
        )
      )
    Await.result(responseFuture, 5.seconds)
    responseFuture.onComplete {
      case Failure(e) =>
        logger.error(s"Cannot create elasticsearch schema: ${e.getStackTrace.mkString("\n")}")
        fail(e)
      case Success(_) => logger.debug("Elasticsearch mapped successfully.")
    }

    val pool: Flow[(HttpRequest, ProposalId), (Try[HttpResponse], ProposalId), Http.HostConnectionPool] =
      Http().cachedHostConnectionPool[ProposalId](
        "localhost",
        elasticsearchExposedPort,
        ConnectionPoolSettings(actorSystem).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[IndexedProposal](proposals).map { proposal =>
      val indexAndDocTypeEndpoint = s"$defaultElasticsearchProposalIndex/$defaultElasticsearchProposalDocType"
      (
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$indexAndDocTypeEndpoint/${proposal.id.value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposal.asJson.toString)
        ),
        proposal.id
      )
    }.via(pool)
      .runForeach {
        case (Failure(e), id) => logger.error(s"Error when indexing proposal ${id.value}:", e)
        case _                =>
      }(ActorMaterializer())
    Await.result(insertFutures, 150.seconds)
    logger.debug("Proposals indexed successfully.")

    val responseRefreshIdeaFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(
        uri = s"$elasticsearchEndpoint/$defaultElasticsearchProposalIndex/_refresh",
        method = HttpMethods.POST
      )
    )
    Await.result(responseRefreshIdeaFuture, 5.seconds)
  }

  val baseQuestion: IndexedProposalQuestion = IndexedProposalQuestion(
    questionId = QuestionId("question-id"),
    "slug",
    "title",
    "question",
    None,
    None,
    isOpen = false
  )

  private val now = DateHelper.now()
  private def newProposal = IndexedProposal(
    id = ProposalId(UUID.randomUUID().toString),
    userId = UserId("user-id"),
    content = "This is a test proposal",
    slug = "this-is-a-test-proposal",
    createdAt = now,
    updatedAt = None,
    votes = Seq(
      IndexedVote
        .empty(VoteKey.Agree)
        .copy(
          qualifications = Seq(
            IndexedQualification.empty(QualificationKey.LikeIt),
            IndexedQualification.empty(QualificationKey.Doable),
            IndexedQualification.empty(QualificationKey.PlatitudeAgree)
          )
        ),
      IndexedVote
        .empty(key = VoteKey.Disagree)
        .copy(
          qualifications = Seq(
            IndexedQualification.empty(QualificationKey.NoWay),
            IndexedQualification.empty(QualificationKey.Impossible),
            IndexedQualification.empty(QualificationKey.PlatitudeDisagree)
          )
        ),
      IndexedVote
        .empty(key = VoteKey.Neutral)
        .copy(
          qualifications = Seq(
            IndexedQualification.empty(QualificationKey.DoNotUnderstand),
            IndexedQualification.empty(QualificationKey.NoOpinion),
            IndexedQualification.empty(QualificationKey.DoNotCare)
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
      IndexedContext(operation = None, location = None, question = None, source = None, getParameters = Seq.empty)
    ),
    author = IndexedAuthor(
      firstName = None,
      organisationName = None,
      organisationSlug = None,
      postalCode = None,
      age = None,
      avatarUrl = None,
      anonymousParticipation = false,
      userType = UserType.UserTypeUser
    ),
    organisations = Seq.empty,
    themeId = None,
    tags = Seq.empty,
    selectedStakeTag = None,
    trending = None,
    labels = Seq(),
    country = Country("FR"),
    language = Language("fr"),
    status = ProposalStatus.Refused,
    ideaId = None,
    operationId = None,
    question = None,
    sequencePool = SequencePool.Tested,
    sequenceSegmentPool = SequencePool.Tested,
    initialProposal = false,
    refusalReason = None,
    operationKind = None,
    segment = None
  )
  private def newTag(label: String, display: Boolean = true) =
    IndexedTag(TagId(UUID.randomUUID().toString), label, display)

  val tagAlpha = newTag("alpha, with a comma")
  val tagBeta = newTag("beta")
  val tagGamma = newTag("gamma")
  val tagDelta = newTag("delta", false)

  private val acceptedProposals: Seq[IndexedProposal] = Seq(
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
      votesCount = 287,
      votesVerifiedCount = 287,
      votesSequenceCount = 287,
      votesSegmentCount = 287,
      toEnrich = true,
      scores = IndexedScores(0, 0, 0, 0, 0, 0, 42, 42, 0, 0, 84, 0),
      segmentScores = IndexedScores(1, 2, 3, 4, 5, 6, 7, 7, 8, 9, 10, 0),
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Craig"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("92876"),
        age = Some(25),
        avatarUrl = Some("avatar.url"),
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = Some(ThemeId("foo-theme")),
      tags = Seq(tagAlpha, tagBeta, tagGamma, tagDelta),
      selectedStakeTag = Some(tagGamma),
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      question = Some(baseQuestion),
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.New,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("9c468c22-1d1a-474b-9081-d79f1079f5e5"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content = "Il faut qu'il/elle interdise les élevages et cultures intensives",
      slug = "il-faut-qu-il-elle-interdise-les-elevages-et-cultures-intensives",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-01T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-01T01:01:01.123Z"))),
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
      votesCount = 310,
      votesVerifiedCount = 310,
      votesSequenceCount = 310,
      votesSegmentCount = 310,
      toEnrich = true,
      scores = IndexedScores(0, 0, 0, 0, 0, 0, 54, 21, 0, 0, 0, 0),
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Valerie"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("41556"),
        age = Some(26),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = Some(ThemeId("foo-theme")),
      tags = Seq(tagAlpha, tagBeta, tagDelta),
      selectedStakeTag = Some(tagBeta),
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      question = Some(baseQuestion),
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("ed8d8b66-579a-48bd-9f61-b7f6cf679e95"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut qu'il/elle privilégie les petites exploitations agricoles aux fermes usines",
      slug = "il-faut-qu-il-elle-privilegie-les-petites-exploitations-agricoles-aux-fermes-usines",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-03T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-03T01:01:01.123Z"))),
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
      votesCount = 127,
      votesVerifiedCount = 127,
      votesSequenceCount = 127,
      votesSegmentCount = 127,
      toEnrich = true,
      scores = IndexedScores(0, 0, 0, 0, 0, 0, 35, 35, 0, 0, 0, 0),
      segmentScores = IndexedScores.empty,
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id-2")),
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Valerie"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("41556"),
        age = Some(26),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq(tagBeta),
      selectedStakeTag = Some(tagBeta),
      operationId = None,
      question = Some(baseQuestion),
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = Some("ubik")
    ),
    IndexedProposal(
      id = ProposalId("c700b4c0-1b49-4373-a993-23c2437e857a"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content =
        "Il faut qu'il/elle protège notre agriculture locale et donne les moyens aux agriculteurs de vivre de leur métier de production",
      slug =
        "il-faut-qu-il-elle-protege-notre-agriculture-locale-et-donne-les-moyens-aux-agriculteurs-de-vivre-de-leur-metier-de-production",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-04T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-04T01:01:01.123Z"))),
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
      votesCount = 353,
      votesVerifiedCount = 353,
      votesSequenceCount = 353,
      votesSegmentCount = 353,
      toEnrich = false,
      scores = IndexedScores(0, 0, 0, 0, 0, 0, 16, 16, 0, 0, 0, 0),
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Jennifer"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("40734"),
        age = Some(23),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq(tagBeta),
      selectedStakeTag = Some(tagDelta),
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id-3")),
      operationId = None,
      question = Some(baseQuestion),
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("eac55aab-021e-495e-9664-bea941b8c51c"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "Il faut qu'il/elle favorise l'accès à l'alimentation issue de l'agriculture biologique",
      slug = "il-faut-qu-il-elle-favorise-l-acces-a-l-alimentation-issue-de-l-agriculture-biologique",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 368,
      votesVerifiedCount = 368,
      votesSequenceCount = 368,
      votesSegmentCount = 368,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Laura"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("43324"),
        age = Some(31),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("5725e8fc-54a1-4b77-9246-d1de60a245c5"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content =
        "Il faut qu'il/elle dissolve la SAFER et ainsi laisser les petits paysans s'installer, avec des petites exploitations",
      slug =
        "il-faut-qu-il-elle-dissolve-la-SAFER-et-ainsi-laisser-les-petits-paysans-s-installer-avec-des-petites-exploitations",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-01T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-01T01:01:01.123Z"))),
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
      votesCount = 305,
      votesVerifiedCount = 305,
      votesSequenceCount = 305,
      votesSegmentCount = 305,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Laura"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("43324"),
        age = Some(31),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("d38244bc-3d39-44a2-bfa9-a30158a297a3"),
      country = Country("IT"),
      language = Language("it"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "C'è bisogno lui / lei deve sostenere e difendere l'agricoltura nel mio dipartimento",
      slug = "c-e-bisogno-lui-lei-deve-sostenere-e-difendere-l-agricoltura-nel-mio-dipartimento",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-05T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-05T01:01:01.123Z"))),
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
      votesCount = 286,
      votesVerifiedCount = 286,
      votesSequenceCount = 286,
      votesSegmentCount = 286,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Laura"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("43324"),
        age = Some(31),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = None,
      question = Some(baseQuestion),
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("ddba011d-5950-4237-bdf1-8bf25473f366"),
      country = Country("IT"),
      language = Language("it"),
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "C'è bisogno lui / lei deve favorire i produttori locali per le mense e i pasti a casa.",
      slug = "c-e-bisogno-lui-lei-deve-favorire-i-produttori-locali-per-le-mense-e-i-pasti-a-casa",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-07T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-07T01:01:01.123Z"))),
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
      votesCount = 162,
      votesVerifiedCount = 162,
      votesSequenceCount = 162,
      votesSegmentCount = 162,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Laura"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("43324"),
        age = Some(31),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = None,
      question = Some(baseQuestion.copy(isOpen = true)),
      sequencePool = SequencePool.Tested,
      sequenceSegmentPool = SequencePool.Tested,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    )
  )

  private val pendingProposals: Seq[IndexedProposal] = Seq(
    IndexedProposal(
      id = ProposalId("7413c8dd-9b17-44be-afc8-fb2898b12773"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content =
        "Il faut qu'il/elle favorise l'agriculture qualitative plut\\u00f4t que l'agriculture intensive (plus de pesticides pour plus de rendements)",
      slug =
        "il-faut-qu-il-elle-favorise-l-agriculture-qualitative-plutot-que-l-agriculture-intensive-plus-de-pesticides-pour-plus-de-rendements",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 178,
      votesVerifiedCount = 178,
      votesSequenceCount = 178,
      votesSegmentCount = 178,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Ronald"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("41556"),
        age = Some(26),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = Some(IdeaId("idea-id")),
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("3bd7ae66-d2b4-42c2-96dd-46dbdb477797"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content =
        "Il faut qu'il/elle vote une loi pour obliger l'industrie pharmaceutique d'investir dans la recherche sur les maladies rares",
      slug =
        "il-faut-qu-il-elle-vote-une-loi-pour-obliger-l-industrie-pharmaceutique-d-investir-dans-la-recherche-sur-les-maladies-rares",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 131,
      votesVerifiedCount = 131,
      votesSequenceCount = 131,
      votesSegmentCount = 131,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Jennifer"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("81966"),
        age = Some(21),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("bd44db77-3096-4e3b-b539-a4038307d85e"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content =
        "Il faut qu'il/elle propose d'interdire aux politiques l'utilisation du big data menant à faire des projets démagogiques",
      slug =
        "il-faut-qu-il-elle-propose-d-interdire-aux-politiques-l-utilisation-du-big-data-menant-a-faire-des-projets-demagogiques",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 356,
      votesVerifiedCount = 356,
      votesSequenceCount = 356,
      votesSegmentCount = 356,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = None,
        organisationName = Some("organisation"),
        organisationSlug = Some("orga"),
        postalCode = Some("40734"),
        age = Some(23),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeOrganisation
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("f2153c81-c031-41f0-8b02-c6ed556d62aa"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("ef418fad-2d2c-4f49-9b36-bf9d6f282aa2"),
      content =
        "Il faut qu'il/elle mette en avant la création de lieux de culture et d'échange, avec quelques petites subventions",
      slug =
        "Il-faut-qu-il-elle-mette-en-avant-la-creation-de-lieux-de-culture-et-d-echange-avec-quelques-petites-subventions",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 347,
      votesVerifiedCount = 347,
      votesSequenceCount = 347,
      votesSegmentCount = 347,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Jennifer - Personality"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("81966"),
        age = Some(21),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypePersonality
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("13b16b9c-9293-4d33-9b82-415264820639"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content = "Il faut qu'il/elle défende un meilleur accès à la culture et à l'éducation pour tous.",
      slug = "il-faut-qu-il-elle-defende-un-meilleur-acces-a-la-culture-et-a-l-education-pour-tous",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 267,
      votesVerifiedCount = 267,
      votesSequenceCount = 267,
      votesSegmentCount = 267,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Craig"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("40734"),
        age = Some(23),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("b3198ad3-ff48-49f2-842c-2aefc3d0df5d"),
      country = Country("FR"),
      language = Language("fr"),
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut qu'il/elle pratique le mécennat et crée des aides pour les artistes, surtout les jeunes.",
      slug = "il-faut-qu-il-elle-pratique-le-mecennat-et-cree-des-aides-pour-les-artistes-surtout-les-jeunes",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 204,
      votesVerifiedCount = 204,
      votesSequenceCount = 204,
      votesSegmentCount = 204,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Valerie"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("92876"),
        age = Some(25),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    ),
    IndexedProposal(
      id = ProposalId("cf940085-010d-46de-8bfd-dee7e8adc8b6"),
      country = Country("IT"),
      language = Language("it"),
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content =
        "C'è bisogno lui / lei deve difendere la Francofonia nel mondo combattendo contro l'egemonia dell'inglese",
      slug = "c'e'bisogno-lui-lei-deve-difendere-la-francofonia-nel-mondo-combattendo-contro-l-egemonia-dell-inglese",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
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
      votesCount = 254,
      votesVerifiedCount = 254,
      votesSequenceCount = 254,
      votesSegmentCount = 254,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = Some(
        IndexedContext(source = None, operation = None, location = None, question = None, getParameters = Seq.empty)
      ),
      trending = None,
      labels = Seq(),
      author = IndexedAuthor(
        firstName = Some("Craig"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("41556"),
        age = Some(26),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      themeId = None,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Pending,
      ideaId = None,
      operationId = None,
      question = None,
      sequencePool = SequencePool.Excluded,
      sequenceSegmentPool = SequencePool.Excluded,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    )
  )

  private def proposals: Seq[IndexedProposal] = acceptedProposals ++ pendingProposals

  feature("get proposal by id") {
    val proposalId = proposals.head.id
    scenario("should return a proposal") {
      whenReady(elasticsearchProposalAPI.findProposalById(proposalId), Timeout(3.seconds)) {
        case Some(proposal) =>
          proposal.id should equal(proposalId)
        case None => fail("proposal not found by id")
      }
    }
  }

  feature("search proposals by content") {
    Given("searching by keywords")
    val query =
      SearchQuery(filters = Some(SearchFilters(content = Some(ContentSearchFilter(text = "Il faut qu'il/elle")))))

    scenario("should return a list of proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
      }
    }
  }

  feature("empty query returns accepted proposals only") {
    Given("searching without query")
    val query = SearchQuery()
    scenario("should return a list of accepted proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(acceptedProposals.size)
      }
    }
  }

  feature("search proposals by status") {
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
    scenario("should return a list of pending proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        info(result.results.map(_.status).mkString)
        result.total should be(pendingProposals.size)
      }
    }
  }

  feature("search proposals by language and/or country") {
    Given("searching by language 'fr' or country 'IT'")
    val queryLanguage =
      SearchQuery(filters = Some(SearchFilters(language = Some(LanguageSearchFilter(language = Language("fr"))))))
    val queryCountry =
      SearchQuery(filters = Some(SearchFilters(country = Some(CountrySearchFilter(country = Country("IT"))))))

    scenario("should return a list of french proposals") {
      whenReady(elasticsearchProposalAPI.searchProposals(queryLanguage), Timeout(3.seconds)) { result =>
        result.total should be(acceptedProposals.count(_.language == Language("fr")))
      }
    }

    scenario("should return a list of proposals from Italy") {
      whenReady(elasticsearchProposalAPI.searchProposals(queryCountry), Timeout(3.seconds)) { result =>
        result.total should be(acceptedProposals.count(_.country == Country("IT")))
      }
    }
  }

  feature("search proposals by slug") {
    scenario("searching a non-existing slug") {
      val query = SearchQuery(Some(SearchFilters(slug = Some(SlugSearchFilter("something-I-dreamt")))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(0)
      }
    }

    scenario("searching an existing slug") {
      val slug = "il-faut-que-mon-ma-depute-fasse-la-promotion-de-la-permaculture"
      val query = SearchQuery(Some(SearchFilters(slug = Some(SlugSearchFilter(slug)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(1)
        result.results.head.slug should be(slug)
      }
    }

    scenario("search proposal by user") {
      val userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5")
      val query = SearchQuery(
        Some(
          SearchFilters(
            status = Some(StatusSearchFilter(Seq(ProposalStatus.Pending, ProposalStatus.Accepted))),
            user = Some(UserSearchFilter(userId))
          )
        )
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(4)
      }
    }

    scenario("search proposals by created date") {

      val searchDate: ZonedDateTime = ZonedDateTime.from(dateFormatter.parse("2017-06-04T01:01:01.123Z"))
      val queryBefore: SearchQuery = SearchQuery(
        Some(SearchFilters(createdAt = Some(CreatedAtSearchFilter(before = Some(searchDate), after = None))))
      )
      val queryAfter: SearchQuery =
        SearchQuery(Some(SearchFilters(createdAt = Some(CreatedAtSearchFilter(None, after = Some(searchDate))))))
      val queryBeforeAfter: SearchQuery =
        SearchQuery(
          Some(
            SearchFilters(
              createdAt = Some(
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

  feature("saving new proposal") {
    scenario("should return distinct new") {
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

  feature("search proposals by toEnrich") {
    scenario("should return a list of proposals") {
      Given("a boolean set to true")
      val query =
        SearchQuery(filters = Some(SearchFilters(toEnrich = Some(ToEnrichSearchFilter(toEnrich = true)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be < 6L
        result.total should be > 0L
      }
    }

    scenario("should not return proposals with no tags") {
      Given("a boolean set to false")
      val query =
        SearchQuery(filters = Some(SearchFilters(toEnrich = Some(ToEnrichSearchFilter(toEnrich = false)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
        result.results.forall(!_.toEnrich) should be(true)
      }
    }
  }

  feature("search proposals by minVotes") {
    scenario("should return a list of proposals") {
      Given("minimum vote number")
      val query =
        SearchQuery(filters = Some(SearchFilters(minVotesCount = Some(MinVotesCountSearchFilter(42)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be > 0L
      }
    }
  }

  feature("search proposals by minScore") {
    scenario("should return a list of proposals") {
      Given("minimum vote number")
      val query =
        SearchQuery(filters = Some(SearchFilters(minScore = Some(MinScoreSearchFilter(42)))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.total should be(1L)
      }
    }
  }

  feature("search proposals by opened question") {
    scenario("should return one proposals") {
      val queryTrue =
        SearchQuery(filters = Some(SearchFilters(questionIsOpen = Some(QuestionIsOpenSearchFilter(true)))))
      val queryFalse =
        SearchQuery(filters = Some(SearchFilters(questionIsOpen = Some(QuestionIsOpenSearchFilter(false)))))

      whenReady(elasticsearchProposalAPI.searchProposals(queryTrue), Timeout(3.seconds)) { result =>
        result.total should be(1L)
      }
      whenReady(elasticsearchProposalAPI.searchProposals(queryFalse), Timeout(3.seconds)) { result =>
        result.total should be(5L)
      }
    }
  }

  feature("search proposals by segment") {
    scenario("search for segment ubik") {
      val query = SearchQuery(filters = Some(SearchFilters(segment = Some(SegmentSearchFilter("ubik")))))

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(1)
        results.results.foreach(_.segment should contain("ubik"))
      }
    }
  }

  feature("search proposals by sequence segment pool") {
    scenario("search for pool new") {
      val query = SearchQuery(
        filters = Some(SearchFilters(sequenceSegmentPool = Some(SequencePoolSearchFilter(SequencePool.New))))
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(1)
        results.results.foreach(_.sequenceSegmentPool should be(SequencePool.New))
        results.results.foreach(_.id.value should be("f4b02e75-8670-4bd0-a1aa-6d91c4de968a"))
      }
    }
  }

  feature("search proposals by author is organisation") {
    scenario("search for author organisation") {
      val query = SearchQuery(
        filters = Some(
          SearchFilters(
            userTypes = Some(UserTypesSearchFilter(Seq(UserType.UserTypeOrganisation))),
            status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
          )
        )
      )

      whenReady(elasticsearchProposalAPI.searchProposals(query), Timeout(10.seconds)) { results =>
        results.results.size should be(1)
        results.results.foreach(_.author.userType should be(UserType.UserTypeOrganisation))
        results.results.foreach(_.id.value should be("bd44db77-3096-4e3b-b539-a4038307d85e"))
      }
    }
  }

  feature("get popular tags") {
    scenario("get tags for base question") {
      whenReady(elasticsearchProposalAPI.getPopularTagsByProposal(baseQuestion.questionId, 10), Timeout(10.seconds)) {
        results =>
          results.size should be(3)
          results.find(_.tagId == tagAlpha.tagId).foreach(_.proposalCount should be(2))
          results.find(_.tagId == tagBeta.tagId).foreach(_.proposalCount should be(4))
          results.find(_.tagId == tagGamma.tagId).foreach(_.proposalCount should be(1))
          results.exists(_.tagId == tagDelta.tagId) shouldBe false
      }
    }

    scenario("get tags for inexistent question") {
      whenReady(elasticsearchProposalAPI.getPopularTagsByProposal(QuestionId("fake"), 10), Timeout(10.seconds)) {
        results =>
          results.size should be(0)
      }
    }
  }

  feature("get top proposals") {
    scenario("get top proposals by idea for base question") {
      whenReady(
        elasticsearchProposalAPI.getTopProposals(baseQuestion.questionId, 10, ProposalElasticsearchFieldNames.ideaId),
        Timeout(10.seconds)
      ) { results =>
        results.take(3).map(_.scores.topScore) should be(Seq(42.0, 35.0, 16.0))
        results.take(3).flatMap(_.ideaId).map(_.value) should be(Seq("idea-id", "idea-id-2", "idea-id-3"))
      }
    }

    scenario("get top proposals by stake tag") {
      whenReady(
        elasticsearchProposalAPI
          .getTopProposals(baseQuestion.questionId, 10, ProposalElasticsearchFieldNames.selectedStakeTagId),
        Timeout(10.seconds)
      ) { results =>
        results.take(3).map(_.scores.topScore) should be(Seq(42.0, 35.0, 16.0))
        results.take(3).flatMap(_.selectedStakeTag).map(_.label) should be(Seq("gamma", "beta", "delta"))
      }
    }
  }

  feature("count proposals by idea") {
    scenario("no ideas") {
      whenReady(elasticsearchProposalAPI.countProposalsByIdea(Seq.empty), Timeout(3.seconds)) { results =>
        results.size shouldBe 0
      }
    }

    scenario("some ideas") {
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

  feature("search proposals by userType") {
    scenario("search for author organisation and personality") {
      val query = SearchQuery(
        filters = Some(
          SearchFilters(
            userTypes = Some(UserTypesSearchFilter(Seq(UserType.UserTypeOrganisation, UserType.UserTypePersonality))),
            status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
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

  feature("get random proposals by top idea") {
    scenario("get random proposals by top idea") {
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
  }
}
