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

package org.make.core.technical
package generator

import _root_.enumeratum.values.scalacheck._
import cats.data.{NonEmptyList => Nel}
import cats.implicits._
import eu.timepit.refined.{refineV, W}
import eu.timepit.refined.api.RefType
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.MaxSize
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import org.make.core.DateHelper._
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, JobStatus}
import org.make.core.operation._
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.technical.generator.CustomGenerators.ImageUrl
import org.make.core.user.{Role, User, UserType}
import org.make.core.{BusinessConfig, DateHelper, RequestContext, SlugHelper}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}

import java.net.URL
import java.time.temporal.ChronoUnit
import java.time.{Period, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.FiniteDuration

trait EntitiesGen extends DateGenerators {

  def genCountryLanguage: Gen[(Country, Language)] =
    Gen.oneOf(for {
      supportedCountry <- BusinessConfig.supportedCountries
      language         <- supportedCountry.supportedLanguages
    } yield (supportedCountry.countryCode, language))

  def genSimpleOperation: Gen[SimpleOperation] =
    for {
      status        <- arbitrary[OperationStatus]
      slug          <- CustomGenerators.LoremIpsumGen.slug(maxLength = Some(20))
      operationKind <- arbitrary[OperationKind]
      date          <- Gen.calendar.map(_.toZonedDateTime)
    } yield SimpleOperation(
      operationId = IdGenerator.uuidGenerator.nextOperationId(),
      status = status,
      slug = slug,
      operationKind = operationKind,
      createdAt = Some(date),
      updatedAt = Some(date)
    )

  def genQuestion(operationId: Option[OperationId]): Gen[Question] =
    for {
      slug                <- CustomGenerators.LoremIpsumGen.slug(maxLength = Some(30))
      (country, language) <- genCountryLanguage
      question            <- CustomGenerators.LoremIpsumGen.sentence()
      shortTitle          <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(30))
    } yield Question(
      questionId = IdGenerator.uuidGenerator.nextQuestionId(),
      slug = slug,
      countries = Nel.of(country),
      language = language,
      question = question,
      shortTitle = Some(shortTitle),
      operationId = operationId
    )

  def genTimelineElement(fromDate: ZonedDateTime): Gen[TimelineElement] =
    for {
      date        <- genDateWithOffset(lowerOffset = Period.ofYears(0), upperOffset = Period.ofYears(3), fromDate = fromDate)
      dateText    <- CustomGenerators.LoremIpsumGen.sentence(Some(20)).map(refineV[MaxSize[W.`20`.T]].unsafeFrom(_))
      description <- CustomGenerators.LoremIpsumGen.sentence(Some(150)).map(refineV[MaxSize[W.`150`.T]].unsafeFrom(_))
    } yield TimelineElement(date = date.toLocalDate, dateText = dateText, description = description)

  def genOOQTimeline(startDate: ZonedDateTime): Gen[OperationOfQuestionTimeline] =
    for {
      result <- genTimelineElement(startDate).asOption
      action <- genTimelineElement(result.map(_.date.atStartOfDay(ZoneOffset.UTC)).getOrElse(startDate)).asOption
      workshop <- genTimelineElement(
        action
          .map(_.date.atStartOfDay(ZoneOffset.UTC))
          .orElse(result.map(_.date.atStartOfDay(ZoneOffset.UTC)))
          .getOrElse(startDate)
      ).asOption
    } yield OperationOfQuestionTimeline(action = action, result = result, workshop = workshop)

  def genOperationOfQuestion: Gen[OperationOfQuestion] =
    for {
      operation <- genSimpleOperation
      question  <- genQuestion(Some(operation.operationId))
      startDate <- genDateWithOffset(lowerOffset = Period.ofYears(-3), upperOffset = Period.ofYears(1))
      endDate <- genDateWithOffset(
        lowerOffset = Period.ofMonths(1),
        upperOffset = Period.ofMonths(6),
        fromDate = startDate
      )
      title             <- CustomGenerators.LoremIpsumGen.sentence()
      canPropose        <- arbitrary[Boolean]
      resultsLink       <- genResultsLink.asOption
      proposalsCount    <- arbitrary[NonNegInt]
      participantsCount <- arbitrary[NonNegInt]
      featured          <- arbitrary[Boolean]
      votesTarget       <- Gen.choose(0, 100_000_000)
      votesCount        <- Gen.choose(0, 100_000_000)
      timeline          <- genOOQTimeline(startDate)
    } yield OperationOfQuestion(
      questionId = question.questionId,
      operationId = operation.operationId,
      startDate = startDate,
      endDate = endDate,
      operationTitle = title,
      landingSequenceId = IdGenerator.uuidGenerator.nextSequenceId(),
      canPropose = canPropose,
      sequenceCardsConfiguration = SequenceCardsConfiguration.default,
      aboutUrl = None,
      metas = Metas(None, None, None),
      theme = QuestionTheme.default,
      description = OperationOfQuestion.defaultDescription,
      consultationImage = None,
      consultationImageAlt = None,
      descriptionImage = None,
      descriptionImageAlt = None,
      resultsLink = resultsLink,
      proposalsCount = proposalsCount,
      participantsCount = participantsCount,
      actions = None,
      featured = featured,
      votesCount = votesCount,
      votesTarget = votesTarget,
      timeline = timeline,
      createdAt = DateHelper.now()
    )

  val genResultsLink: Gen[ResultsLink] = Gen.frequency(
    (4, Gen.oneOf(ResultsLink.Internal.values)),
    (1, ImageUrl.gen(100, 100).map(url => ResultsLink.External(new URL(url))))
  )

  def genRoles: Gen[Seq[Role]] = {
    val roles = Gen.frequency(
      (1, Role.RoleActor),
      (1, Role.RoleAdmin),
      (9, Role.RoleCitizen),
      (2, Role.RoleModerator),
      (1, Role.RolePolitical)
    )
    Gen.listOfN(3, roles).map(_.distinct)
  }

  private val SumCounterUpperBound = 600
  private val SimpleCountUpperBound = SumCounterUpperBound / 3
  private def getMaxCount(getter: Counts => Int, countsNel: Nel[Counts]): Int =
    getter(countsNel.maximumBy(getter))

  private def genSimpleCounts: Gen[Counts] =
    for {
      count         <- Gen.chooseNum(0, SimpleCountUpperBound)
      countVerified <- Gen.chooseNum(0, count)
      countSequence <- Gen.chooseNum(0, countVerified)
      countSegment  <- Gen.chooseNum(0, countSequence)
    } yield Counts(count, countVerified, countSequence, countSegment)

  private def genSumCounts(counters: Nel[Counts]): Gen[Counts] =
    for {
      count         <- Gen.chooseNum(getMaxCount(_.count, counters), SumCounterUpperBound)
      countVerified <- Gen.chooseNum(getMaxCount(_.verified, counters), count)
      countSequence <- Gen.chooseNum(getMaxCount(_.sequence, counters), countVerified)
      countSegment  <- Gen.chooseNum(getMaxCount(_.segment, counters), countSequence)
    } yield Counts(count, countVerified, countSequence, countSegment)

  def genProposalVotes: Gen[Seq[Vote]] =
    for {
      likeItCounts            <- genSimpleCounts
      doableCounts            <- genSimpleCounts
      platitudeAgreeCounts    <- genSimpleCounts
      agreeCounts             <- genSumCounts(Nel.of(likeItCounts, doableCounts, platitudeAgreeCounts))
      doNotUnderstandCounts   <- genSimpleCounts
      doNotCareCounts         <- genSimpleCounts
      noOpinionCounts         <- genSimpleCounts
      neutralCounts           <- genSumCounts(Nel.of(doNotUnderstandCounts, doNotCareCounts, noOpinionCounts))
      noWayCounts             <- genSimpleCounts
      impossibleCount         <- genSimpleCounts
      platitudeDisagreeCounts <- genSimpleCounts
      disagreeCounts          <- genSumCounts(Nel.of(noWayCounts, impossibleCount, platitudeDisagreeCounts))
    } yield Seq(
      Vote(
        key = VoteKey.Agree,
        count = agreeCounts.count,
        countVerified = agreeCounts.verified,
        countSequence = agreeCounts.sequence,
        countSegment = agreeCounts.segment,
        qualifications = Seq(
          Qualification(
            QualificationKey.LikeIt,
            likeItCounts.count,
            likeItCounts.verified,
            likeItCounts.sequence,
            likeItCounts.segment
          ),
          Qualification(
            QualificationKey.Doable,
            doableCounts.count,
            doableCounts.verified,
            doableCounts.sequence,
            doableCounts.segment
          ),
          Qualification(
            QualificationKey.PlatitudeAgree,
            platitudeAgreeCounts.count,
            platitudeAgreeCounts.verified,
            platitudeAgreeCounts.sequence,
            platitudeAgreeCounts.segment
          )
        )
      ),
      Vote(
        key = VoteKey.Neutral,
        count = neutralCounts.count,
        countVerified = neutralCounts.verified,
        countSequence = neutralCounts.sequence,
        countSegment = neutralCounts.segment,
        qualifications = Seq(
          Qualification(
            QualificationKey.DoNotUnderstand,
            doNotUnderstandCounts.count,
            doNotUnderstandCounts.verified,
            doNotUnderstandCounts.sequence,
            doNotUnderstandCounts.segment
          ),
          Qualification(
            QualificationKey.DoNotCare,
            doNotCareCounts.count,
            doNotCareCounts.verified,
            doNotCareCounts.sequence,
            doNotCareCounts.segment
          ),
          Qualification(
            QualificationKey.NoOpinion,
            noOpinionCounts.count,
            noOpinionCounts.verified,
            noOpinionCounts.sequence,
            noOpinionCounts.segment
          )
        )
      ),
      Vote(
        key = VoteKey.Disagree,
        count = disagreeCounts.count,
        countVerified = disagreeCounts.verified,
        countSequence = disagreeCounts.sequence,
        countSegment = disagreeCounts.segment,
        qualifications = Seq(
          Qualification(
            QualificationKey.NoWay,
            noWayCounts.count,
            noWayCounts.verified,
            noWayCounts.sequence,
            noWayCounts.segment
          ),
          Qualification(
            QualificationKey.Impossible,
            impossibleCount.count,
            impossibleCount.verified,
            impossibleCount.sequence,
            impossibleCount.segment
          ),
          Qualification(
            QualificationKey.PlatitudeDisagree,
            disagreeCounts.count,
            disagreeCounts.verified,
            disagreeCounts.sequence,
            disagreeCounts.segment
          )
        )
      )
    )

  implicit val arbProposalStatus: Arbitrary[ProposalStatus] = Arbitrary(
    Gen.frequency(
      (10, ProposalStatus.Pending),
      (5, ProposalStatus.Postponed),
      (70, ProposalStatus.Accepted),
      (14, ProposalStatus.Refused),
      (1, ProposalStatus.Archived)
    )
  )

  def genProposal(question: Question, users: Seq[User], tagsIds: Seq[TagId]): Gen[Proposal] = {
    val maxLength: Option[PosInt] = RefType.applyRef[PosInt](BusinessConfig.defaultProposalMaxLength).toOption
    for {
      content         <- CustomGenerators.LoremIpsumGen.sentence(maxLength).map(sentence => s"Il faut ${sentence.toLowerCase}")
      author          <- Gen.oneOf(users.map(_.userId))
      status          <- arbitrary[ProposalStatus]
      refusalReason   <- CustomGenerators.LoremIpsumGen.word
      tags            <- Gen.someOf(tagsIds)
      votes           <- genProposalVotes
      organisationIds <- Gen.someOf(users.filter(_.userType == UserType.UserTypeOrganisation).map(_.userId))
      date            <- Gen.calendar.map(_.toZonedDateTime).asOption
      initialProposal <- Gen.frequency((9, false), (1, true))
      keywords        <- Gen.listOf(genKeyword)
    } yield Proposal(
      proposalId = IdGenerator.uuidGenerator.nextProposalId(),
      slug = SlugHelper(content),
      content = content,
      author = author,
      labels = Seq.empty,
      status = status,
      refusalReason = if (status == ProposalStatus.Refused) Some(refusalReason) else None,
      tags = tags.toSeq,
      votes = votes,
      organisationIds = organisationIds.toSeq,
      questionId = Some(question.questionId),
      creationContext = RequestContext.empty,
      idea = None,
      operation = question.operationId,
      createdAt = date,
      updatedAt = date,
      events = List.empty,
      initialProposal = initialProposal,
      keywords = keywords
    )
  }

  val genJob: Gen[Job] = {
    val genJobStatus: Gen[JobStatus] = Gen.oneOf(
      Arbitrary.arbitrary[Job.Progress].map(JobStatus.Running.apply),
      Arbitrary.arbitrary[Option[String]].map(JobStatus.Finished.apply)
    )
    for {
      id        <- Gen.uuid
      status    <- genJobStatus
      createdAt <- genDateWithOffset(lowerOffset = Period.ofYears(-2), upperOffset = Period.ZERO).asOption
      update <- Arbitrary
        .arbitrary[Option[FiniteDuration]]
        .map(_.flatMap(u => createdAt.map(_.plusNanos(u.toNanos).truncatedTo(ChronoUnit.MILLIS))))
    } yield Job(JobId(id.toString), status, createdAt, update)
  }

  private val stake = TagTypeId("c0d8d858-8b04-4dd9-add6-fa65443b622b")
  private val solution = TagTypeId("cc6a16a5-cfa7-495b-a235-08affb3551af")
  private val moment = TagTypeId("5e539923-c265-45d2-9d0b-77f29c8b0a06")
  private val target = TagTypeId("226070ac-51b0-4e92-883a-f0a24d5b8525")
  private val actor = TagTypeId("982e6860-eb66-407e-bafb-461c2d927478")
  private val legacy = TagTypeId("8405aba4-4192-41d2-9a0d-b5aa6cb98d37")

  def genTag(operationId: Option[OperationId], questionId: Option[QuestionId]): Gen[Tag] =
    for {
      label     <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(20))
      weight    <- Gen.posNum[Float]
      display   <- Gen.frequency((8, TagDisplay.Inherit), (1, TagDisplay.Displayed), (1, TagDisplay.Hidden))
      tagTypeId <- Gen.oneOf(Seq(stake, solution, moment, target, actor, legacy))
    } yield Tag(
      tagId = IdGenerator.uuidGenerator.nextTagId(),
      label = label,
      display = display,
      tagTypeId = tagTypeId,
      weight = weight,
      operationId = operationId,
      questionId = questionId
    )

  def genKeyword: Gen[ProposalKeyword] = {
    for {
      key   <- CustomGenerators.LoremIpsumGen.word.map(ProposalKeywordKey.apply)
      label <- CustomGenerators.LoremIpsumGen.word
    } yield ProposalKeyword(key = key, label = label)
  }

}

final case class Counts(count: Int, verified: Int, sequence: Int, segment: Int)
