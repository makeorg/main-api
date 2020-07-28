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

import java.net.URL
import java.time.temporal.ChronoUnit

import _root_.enumeratum.values.scalacheck._
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.api.RefType
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import org.make.core.DateHelper._
import org.make.core.{BusinessConfig, RequestContext, SlugHelper}
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, JobStatus}
import org.make.core.operation.{
  Metas,
  OperationId,
  OperationKind,
  OperationOfQuestion,
  OperationStatus,
  QuestionTheme,
  ResultsLink,
  SequenceCardsConfiguration,
  SimpleOperation
}
import org.make.core.proposal.{Proposal, ProposalStatus, Qualification, QualificationKey, Vote, VoteKey}
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.technical.generator.CustomGenerators.ImageUrl
import org.make.core.user.{Role, User, UserType}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import scala.concurrent.duration.FiniteDuration

trait EntitiesGen {

  def genCountryLanguage: Gen[(Country, Language)] =
    Gen.oneOf(for {
      supportedCountry <- BusinessConfig.supportedCountries
      language         <- supportedCountry.supportedLanguages
    } yield (supportedCountry.countryCode, language))

  def genSimpleOperation: Gen[SimpleOperation] =
    for {
      status               <- arbitrary[OperationStatus]
      slug                 <- CustomGenerators.LoremIpsumGen.slug(maxLength = Some(20))
      allowedSources       <- CustomGenerators.LoremIpsumGen.words
      (_, defaultLanguage) <- genCountryLanguage
      operationKind        <- arbitrary[OperationKind]
      date                 <- Gen.calendar.map(_.toZonedDateTime)
    } yield SimpleOperation(
      operationId = IdGenerator.uuidGenerator.nextOperationId(),
      status = status,
      slug = slug,
      allowedSources = allowedSources,
      defaultLanguage = defaultLanguage,
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
      country = country,
      language = language,
      question = question,
      shortTitle = Some(shortTitle),
      operationId = operationId
    )

  def genOperationOfQuestion: Gen[OperationOfQuestion] =
    for {
      operation         <- genSimpleOperation
      question          <- genQuestion(Some(operation.operationId))
      startDate         <- Gen.option(CustomGenerators.Time.zonedDateTime)
      endDate           <- Gen.option(CustomGenerators.Time.zonedDateTime.suchThat(date => startDate.forall(_.isBefore(date))))
      title             <- CustomGenerators.LoremIpsumGen.sentence()
      canPropose        <- arbitrary[Boolean]
      resultsLink       <- Gen.option(genResultsLink)
      proposalsCount    <- arbitrary[NonNegInt]
      participantsCount <- arbitrary[NonNegInt]
      featured          <- arbitrary[Boolean]
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
      featured = featured
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

  private def genCounts: Gen[Counts] =
    for {
      count         <- Gen.posNum[Int]
      countVerified <- Gen.chooseNum[Int](0, count)
      countSequence <- Gen.chooseNum[Int](0, count)
      countSegment  <- Gen.chooseNum[Int](0, count)
    } yield Counts(count, countVerified, countSequence, countSegment)

  def genProposalVotes: Gen[Seq[Vote]] = {
    for {
      countsAgree                   <- genCounts
      countsQualifLikeIt            <- genCounts
      countsQualifDoable            <- genCounts
      countsQualifPlatitudeAgree    <- genCounts
      countsNeutral                 <- genCounts
      countsQualifDoNotUnderstand   <- genCounts
      countsQualifDoNotCare         <- genCounts
      countsQualifNoOpinion         <- genCounts
      countsDisagree                <- genCounts
      countsQualifNoWay             <- genCounts
      countsQualifImpossible        <- genCounts
      countsQualifPlatitudeDisagree <- genCounts
    } yield Seq(
      Vote(
        key = VoteKey.Agree,
        count = countsAgree.count,
        countVerified = countsAgree.verified,
        countSequence = countsAgree.sequence,
        countSegment = countsAgree.segment,
        qualifications = Seq(
          Qualification(
            QualificationKey.LikeIt,
            countsQualifLikeIt.count,
            countsQualifLikeIt.verified,
            countsQualifLikeIt.sequence,
            countsQualifLikeIt.segment
          ),
          Qualification(
            QualificationKey.Doable,
            countsQualifDoable.count,
            countsQualifDoable.verified,
            countsQualifDoable.sequence,
            countsQualifDoable.segment
          ),
          Qualification(
            QualificationKey.PlatitudeAgree,
            countsQualifPlatitudeAgree.count,
            countsQualifPlatitudeAgree.verified,
            countsQualifPlatitudeAgree.sequence,
            countsQualifPlatitudeAgree.segment
          )
        )
      ),
      Vote(
        key = VoteKey.Neutral,
        count = countsNeutral.count,
        countVerified = countsNeutral.verified,
        countSequence = countsNeutral.sequence,
        countSegment = countsNeutral.segment,
        qualifications = Seq(
          Qualification(
            QualificationKey.DoNotUnderstand,
            countsQualifDoNotUnderstand.count,
            countsQualifDoNotUnderstand.verified,
            countsQualifDoNotUnderstand.sequence,
            countsQualifDoNotUnderstand.segment
          ),
          Qualification(
            QualificationKey.DoNotCare,
            countsQualifDoNotCare.count,
            countsQualifDoNotCare.verified,
            countsQualifDoNotCare.sequence,
            countsQualifDoNotCare.segment
          ),
          Qualification(
            QualificationKey.NoOpinion,
            countsQualifNoOpinion.count,
            countsQualifNoOpinion.verified,
            countsQualifNoOpinion.sequence,
            countsQualifNoOpinion.segment
          )
        )
      ),
      Vote(
        key = VoteKey.Disagree,
        count = countsDisagree.count,
        countVerified = countsDisagree.verified,
        countSequence = countsDisagree.sequence,
        countSegment = countsDisagree.segment,
        qualifications = Seq(
          Qualification(
            QualificationKey.NoWay,
            countsQualifNoWay.count,
            countsQualifNoWay.verified,
            countsQualifNoWay.sequence,
            countsQualifNoWay.segment
          ),
          Qualification(
            QualificationKey.Impossible,
            countsQualifImpossible.count,
            countsQualifImpossible.verified,
            countsQualifImpossible.sequence,
            countsQualifImpossible.segment
          ),
          Qualification(
            QualificationKey.PlatitudeDisagree,
            countsQualifPlatitudeDisagree.count,
            countsQualifPlatitudeDisagree.verified,
            countsQualifPlatitudeDisagree.sequence,
            countsQualifPlatitudeDisagree.segment
          )
        )
      )
    )
  }

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
      date            <- Gen.option(Gen.calendar.map(_.toZonedDateTime))
      initialProposal <- Gen.frequency((9, false), (1, true))
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
      language = Some(question.language),
      country = Some(question.country),
      questionId = Some(question.questionId),
      creationContext = RequestContext.empty,
      idea = None,
      operation = question.operationId,
      createdAt = date,
      updatedAt = date,
      events = List.empty,
      initialProposal = initialProposal
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
      createdAt <- Gen.option(CustomGenerators.Time.zonedDateTime)
      update <- Arbitrary
        .arbitrary[Option[FiniteDuration]]
        .map(_.flatMap(u => createdAt.map(_.plusNanos(u.toNanos).truncatedTo(ChronoUnit.MILLIS))))
    } yield Job(JobId(id.toString), status, createdAt, update)
  }

}

final case class Counts(count: Int, verified: Int, sequence: Int, segment: Int)
