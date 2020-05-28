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

package org.make.api.technical.generator

import eu.timepit.refined.api.RefType
import eu.timepit.refined.scalacheck.numeric._
import java.time.temporal.ChronoUnit
import org.make.api.operation.CreateOperationOfQuestion
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.UserRegisterData
import org.make.core.job.Job
import org.make.core.job.Job.{JobId, JobStatus}
import org.make.core.operation.{OperationId, OperationKind, OperationStatus, SimpleOperation}
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{Role, User, UserType}
import org.make.core.{BusinessConfig, RequestContext, SlugHelper}
import org.scalacheck.{Arbitrary, Gen}
import org.make.core.DateHelper._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt

import scala.concurrent.duration.FiniteDuration

object EntitiesGen extends DefaultIdGeneratorComponent {

  def genCountryLanguage: Gen[(Country, Language)] =
    Gen.oneOf(for {
      supportedCountry <- BusinessConfig.supportedCountries
      language         <- supportedCountry.supportedLanguages
    } yield (supportedCountry.countryCode, language))

  def genSimpleOperation: Gen[SimpleOperation] =
    for {
      status               <- Gen.oneOf(OperationStatus.statusMap.values.toSeq)
      slug                 <- CustomGenerators.LoremIpsumGen.slug(maxLength = Some(20))
      allowedSources       <- CustomGenerators.LoremIpsumGen.words
      (_, defaultLanguage) <- genCountryLanguage
      operationKind        <- Gen.oneOf(OperationKind.kindMap.values.toSeq)
      date                 <- Gen.calendar.map(_.toZonedDateTime)
    } yield SimpleOperation(
      operationId = idGenerator.nextOperationId(),
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
      questionId = idGenerator.nextQuestionId(),
      slug = slug,
      country = country,
      language = language,
      question = question,
      shortTitle = Some(shortTitle),
      operationId = operationId
    )

  def genCreateOperationOfQuestion(operationId: OperationId): Gen[CreateOperationOfQuestion] =
    for {
      date                <- Gen.option(Gen.calendar.map(_.toZonedDateTime))
      operationTitle      <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(150))
      shortTitle          <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(30))
      slug                <- CustomGenerators.LoremIpsumGen.slug(maxLength = Some(30))
      (country, language) <- genCountryLanguage
      question            <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(150))
      consultationImage   <- CustomGenerators.ImageUrl.gen(width = 300, height = 100)
      descriptionImage    <- CustomGenerators.ImageUrl.gen(width = 300, height = 100)
    } yield CreateOperationOfQuestion(
      operationId = operationId,
      startDate = date,
      endDate = date.map(_.plusMonths(3)),
      operationTitle = operationTitle,
      slug = slug,
      country = country,
      language = language,
      question = question,
      shortTitle = Some(shortTitle),
      consultationImage = Some(consultationImage),
      descriptionImage = Some(descriptionImage),
      actions = None
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

  def genUserRegisterData(questionId: Option[QuestionId]): Gen[UserRegisterData] =
    for {
      email       <- CustomGenerators.Mail.gen
      firstName   <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      lastName    <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      dateOfBirth <- Gen.option(Gen.calendar.map(_.toZonedDateTime.toLocalDate))
      profession  <- Gen.option(CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(15)))
      postalCode  <- Gen.option(CustomGenerators.PostalCode.gen)
      gender      <- Gen.option(Gen.oneOf(Gender.genders.values.toSeq))
      socioProfessionalCategory <- Gen.option(
        Gen.oneOf(SocioProfessionalCategory.socioProfessionalCategories.values.toSeq)
      )
      (country, language) <- genCountryLanguage
      optIn               <- Gen.option(Arbitrary.arbitrary[Boolean])
      optInPartner        <- Gen.option(Arbitrary.arbitrary[Boolean])
      roles               <- genRoles
      politicalParty      <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      publicProfile       <- Arbitrary.arbitrary[Boolean]
    } yield UserRegisterData(
      email = email,
      firstName = firstName,
      lastName = lastName,
      password = Some(email),
      lastIp = None,
      dateOfBirth = dateOfBirth,
      profession = profession,
      postalCode = postalCode,
      gender = gender,
      socioProfessionalCategory = socioProfessionalCategory,
      country = country,
      language = language,
      questionId = questionId,
      optIn = optIn,
      optInPartner = optInPartner,
      roles = roles,
      availableQuestions = questionId.toSeq,
      politicalParty = politicalParty,
      website = None,
      publicProfile = publicProfile
    )

  def genCounts: Gen[Counts] =
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
      status          <- Gen.oneOf(ProposalStatus.statusMap.values.toSeq)
      refusalReason   <- CustomGenerators.LoremIpsumGen.word
      tags            <- Gen.someOf(tagsIds)
      votes           <- genProposalVotes
      organisationIds <- Gen.someOf(users.filter(_.userType == UserType.UserTypeOrganisation).map(_.userId))
      date            <- Gen.option(Gen.calendar.map(_.toZonedDateTime))
      initialProposal <- Gen.frequency((9, false), (1, true))
    } yield Proposal(
      proposalId = idGenerator.nextProposalId(),
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
