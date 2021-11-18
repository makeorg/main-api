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

package org.make.core.proposal.indexed

import cats.data.{NonEmptyList => Nel}
import org.make.core.BaseUnitTest
import com.github.plokhotnyuk.jsoniter_scala.core._
import org.make.core.jsoniter.JsoniterCodecs
import java.time.ZonedDateTime
import org.make.core.tag.TagId
import org.make.core.idea.IdeaId
import org.make.core.reference.Country
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.operation.{OperationId, OperationKind}
import cats.data.NonEmptyList
import org.make.api.proposal.ProposalScorer
import org.make.core.proposal.ProposalId
import org.make.core.user.UserId
import org.make.core.proposal._
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.core.user._
import org.make.core.RequestContext
import scala.io.Source

class ProposalTest extends BaseUnitTest with JsoniterCodecs {

  private def jsoniterRoundtrip[T](value: T)(implicit codec: JsonValueCodec[T]): Unit =
    readFromString[T](writeToString(value)) shouldEqual value

  val questionId = readFromString[QuestionId](""""a74c509e-0028-4e46-991b-6f66bdd2b9a4"""")
  val slug = "foo"
  val title = "bar"
  val question = "baz"
  val str = """"FRA""""
  val country = readFromString[Country](str)
  val countries = Nel.of(country)
  val language = readFromString[Language](str)
  val startDate = readFromString[ZonedDateTime](""""2021-06-23T02:12:50.881Z"""")
  val endDate = readFromString[ZonedDateTime](""""2021-06-24T02:12:50.881Z"""")
  val isOpen = true
  val indexedQuestion =
    IndexedProposalQuestion(questionId, slug, title, question, countries, language, startDate, endDate, isOpen)

  Feature("The IndexedProposal codec") {
    Scenario("ZonedDateTime") {

      jsoniterRoundtrip[ZonedDateTime](startDate)
    }

    Scenario("Country") {

      jsoniterRoundtrip[Country](country)
    }

    Scenario("Sample indexed question") {

      jsoniterRoundtrip[IndexedProposalQuestion](indexedQuestion)
    }

    val operation = Some(OperationId("kek"))
    val source = Some("kek")
    val location = Some("kek")
    val getParameters = Seq(IndexedGetParameters("foo", "bar"))
    val indexedContext =
      IndexedContext(operation, source, location, Some(question), Some(countries.head), Some(language), getParameters)

    Scenario("Sample Nel[Country]") {

      jsoniterRoundtrip[Nel[Country]](countries)
    }

    Scenario("Sample IndexedContext") {

      jsoniterRoundtrip[IndexedContext](indexedContext)
    }

    Scenario("Sequence pool") {

      val seqPool: SequencePool = SequencePool.Tested
      jsoniterRoundtrip[SequencePool](seqPool)
    }

    val author: IndexedAuthor = IndexedAuthor(
      firstName = Some("be766839-a7c0-4ee6-93a4-96fdd924f9dd"),
      displayName = Some("be766839-a7c0-4ee6-93a4-96fdd924f9dd"),
      organisationName = None,
      organisationSlug = None,
      postalCode = Some("12345"),
      age = Some(45),
      avatarUrl = None,
      anonymousParticipation = false,
      userType = UserType.UserTypeUser,
      profession = Some("des choses")
    )

    val regularScore = ProposalScorer(Seq.empty, VotesCounter.SequenceVotesCounter, 0.5)
    val segmentScore = ProposalScorer(Seq.empty, VotesCounter.SegmentVotesCounter, 0.5)
    val proposal =
      IndexedProposal(
        id = ProposalId("bf75cfa9-9fc6-4f36-82ff-9ed378df1742"),
        userId = UserId("d22a234d-13d4-44c2-974e-8a4cb26ce47d"),
        content = "Il faut tester la tenue en charge de l'API",
        slug = "il-faut-tester-la-tenue-en-charge-de-l-api",
        status = org.make.core.proposal.ProposalStatus.Pending,
        createdAt = ZonedDateTime.parse("2021-06-23T02:12:50.881Z"),
        updatedAt = None,
        votes = Seq(
          IndexedVote(
            key = VoteKey.Agree,
            count = 0,
            countVerified = 0,
            countSequence = 0,
            countSegment = 0,
            qualifications = Seq(
              IndexedQualification(
                key = QualificationKey.LikeIt,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              ),
              IndexedQualification(
                key = QualificationKey.Doable,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              ),
              IndexedQualification(
                key = QualificationKey.PlatitudeAgree,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              )
            )
          ),
          IndexedVote(
            key = VoteKey.Disagree,
            count = 0,
            countVerified = 0,
            countSequence = 0,
            countSegment = 0,
            qualifications = Seq(
              IndexedQualification(
                key = QualificationKey.NoWay,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              ),
              IndexedQualification(
                key = QualificationKey.Impossible,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              ),
              IndexedQualification(
                key = QualificationKey.PlatitudeDisagree,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              )
            )
          ),
          IndexedVote(
            key = VoteKey.Neutral,
            count = 0,
            countVerified = 0,
            countSequence = 0,
            countSegment = 0,
            qualifications = Seq(
              IndexedQualification(
                key = QualificationKey.DoNotUnderstand,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              ),
              IndexedQualification(
                key = QualificationKey.NoOpinion,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              ),
              IndexedQualification(
                key = QualificationKey.DoNotCare,
                count = 0,
                countVerified = 0,
                countSequence = 0,
                countSegment = 0
              )
            )
          )
        ),
        votesCount = 0,
        votesVerifiedCount = 0,
        votesSequenceCount = 0,
        votesSegmentCount = 0,
        toEnrich = false,
        scores = IndexedScores(regularScore),
        segmentScores = IndexedScores(segmentScore),
        agreementRate = 0.0,
        context = Some(IndexedContext.apply(RequestContext.empty.copy(source = Some("core")), false)),
        trending = Some("foo"),
        labels = Seq("hello, I am a label", "me too!"),
        author = author,
        organisations = Seq(
          IndexedOrganisationInfo(
            organisationId = UserId("1"),
            organisationName = Some("foo"),
            organisationSlug = Some("bar")
          )
        ),
        question = Some(
          IndexedProposalQuestion(
            questionId = QuestionId("a74c509e-0028-4e46-991b-6f66bdd2b9a4"),
            slug = "load-testing",
            title = "Opération spécifique aux tests de charge",
            question = "Comment tester la bonne tenue en charge de l'API ?",
            countries = NonEmptyList.of(Country("FR")),
            language = Language("fr"),
            startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
            endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
            isOpen = true
          )
        ),
        tags = Seq(IndexedTag(tagId = TagId("bonjour"), label = "hola", display = true)),
        selectedStakeTag =
          Some(IndexedTag(tagId = TagId("I am a tag ID"), label = "I, on the other hand, am a label", display = false)),
        ideaId = Some(IdeaId("foo")),
        operationId = Some(OperationId("bar")),
        sequencePool = SequencePool.New,
        sequenceSegmentPool = SequencePool.New,
        initialProposal = false,
        refusalReason = Some("dolor sit amet"),
        operationKind = Some(OperationKind.GreatCause),
        segment = Some("test"),
        keywords = Seq(IndexedProposalKeyword(key = ProposalKeywordKey("kek"), label = "Je suis un label"))
      )

    Scenario("IndexedProposal codec") {
      jsoniterRoundtrip[IndexedProposal](proposal)
    }

    Scenario("IndexedProposal JSON decoding") {

      Given("a JSON payload from ElasticSearch")

      val payload = Source.fromResource("proposalfixture.json").getLines().map(_.trim).mkString("")

      Then("it decodes the payload correctly")

      readFromString[IndexedProposal](payload)
    }
  }
}
