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

import java.time.ZonedDateTime

import org.make.api.operation.CreateOperationOfQuestion
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.UserRegisterData
import org.make.core.operation.{OperationId, OperationKind, OperationStatus, SimpleOperation}
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{Role, User, UserType}
import org.make.core.{BusinessConfig, RequestContext, SlugHelper}
import org.scalacheck.Gen

object EntitiesGen extends DefaultIdGeneratorComponent {

  def genCountryLanguage: Gen[(Country, Language)] =
    Gen.oneOf(BusinessConfig.supportedCountries.map(cl => (cl.countryCode, cl.defaultLanguage)))

  def genSimpleOperation: Gen[SimpleOperation] =
    for {
      status               <- Gen.oneOf(OperationStatus.statusMap.values.toSeq)
      slug                 <- CustomGenerators.LoremIpsumGen.slug(maxLength = 256)
      allowedSources       <- CustomGenerators.LoremIpsumGen.words
      (_, defaultLanguage) <- genCountryLanguage
      operationKind        <- Gen.oneOf(OperationKind.kindMap.values.toSeq)
      date                 <- Gen.calendar.map(c => ZonedDateTime.ofInstant(c.toInstant, c.getTimeZone.toZoneId))
    } yield
      SimpleOperation(
        operationId = idGenerator.nextOperationId(),
        status = status,
        slug = slug,
        allowedSources = allowedSources,
        defaultLanguage = defaultLanguage,
        operationKind = operationKind,
        createdAt = Some(date),
        updatedAt = Some(date),
      )

  def genQuestion(operationId: Option[OperationId]): Gen[Question] =
    for {
      slug                <- CustomGenerators.LoremIpsumGen.slug(maxLength = 30)
      (country, language) <- genCountryLanguage
      question            <- CustomGenerators.LoremIpsumGen.sentence()
    } yield
      Question(
        questionId = idGenerator.nextQuestionId(),
        slug = slug,
        country = country,
        language = language,
        question = question,
        operationId = operationId
      )

  def genCreateOperationOfQuestion(operationId: OperationId): Gen[CreateOperationOfQuestion] =
    for {
      date                <- Gen.option(Gen.calendar.map(_.toZonedDateTime))
      operationTitle      <- CustomGenerators.LoremIpsumGen.sentence(maxLength = 512)
      slug                <- CustomGenerators.LoremIpsumGen.slug(maxLength = 30)
      (country, language) <- genCountryLanguage
      question            <- CustomGenerators.LoremIpsumGen.sentence()
      consultationImage   <- CustomGenerators.Image.url(width = 300, height = 100)
    } yield
      CreateOperationOfQuestion(
        operationId = operationId,
        startDate = date,
        endDate = date.map(_.plusMonths(3)),
        operationTitle = operationTitle,
        slug = slug,
        country = country,
        language = language,
        question = question,
        consultationImage = Some(consultationImage)
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
      email       <- CustomGenerators.Mail.safe
      firstName   <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      lastName    <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      dateOfBirth <- Gen.option(Gen.calendar.map(_.toZonedDateTime.toLocalDate))
      profession  <- Gen.option(CustomGenerators.LoremIpsumGen.sentence(maxLength = 15))
      postalCode  <- Gen.option(Gen.listOfN(5, Gen.numChar).map(_.mkString))
      gender      <- Gen.option(Gen.oneOf(Gender.genders.values.toSeq))
      socioProfessionalCategory <- Gen.option(
        Gen.oneOf(SocioProfessionalCategory.socioProfessionalCategories.values.toSeq)
      )
      (country, language) <- genCountryLanguage
      optIn               <- Gen.option(Gen.oneOf(Seq(true, false)))
      optInPartner        <- Gen.option(Gen.oneOf(Seq(true, false)))
      roles               <- genRoles
      politicalParty      <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      publicProfile       <- Gen.oneOf(Seq(true, false))
    } yield
      UserRegisterData(
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
        publicProfile = publicProfile,
      )

  def genProposalVotes: Gen[Seq[Vote]] = {
    def genCounts: Gen[(Int, Int, Int, Int)] =
      for {
        count         <- Gen.posNum[Int]
        countVerified <- Gen.chooseNum[Int](0, count)
        countSequence <- Gen.chooseNum[Int](0, count)
        countSegment  <- Gen.chooseNum[Int](0, count)
      } yield (count, countVerified, countSequence, countSegment)
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
    } yield
      Seq(
        Vote(
          key = VoteKey.Agree,
          count = countsAgree._1,
          countVerified = countsAgree._2,
          countSequence = countsAgree._3,
          countSegment = countsAgree._4,
          qualifications = Seq(
            Qualification(
              QualificationKey.LikeIt,
              countsQualifLikeIt._1,
              countsQualifLikeIt._2,
              countsQualifLikeIt._3,
              countsQualifLikeIt._4
            ),
            Qualification(
              QualificationKey.Doable,
              countsQualifDoable._1,
              countsQualifDoable._2,
              countsQualifDoable._3,
              countsQualifDoable._4
            ),
            Qualification(
              QualificationKey.PlatitudeAgree,
              countsQualifPlatitudeAgree._1,
              countsQualifPlatitudeAgree._2,
              countsQualifPlatitudeAgree._3,
              countsQualifPlatitudeAgree._4
            )
          )
        ),
        Vote(
          key = VoteKey.Neutral,
          count = countsNeutral._1,
          countVerified = countsNeutral._2,
          countSequence = countsNeutral._3,
          countSegment = countsNeutral._4,
          qualifications = Seq(
            Qualification(
              QualificationKey.DoNotUnderstand,
              countsQualifDoNotUnderstand._1,
              countsQualifDoNotUnderstand._2,
              countsQualifDoNotUnderstand._3,
              countsQualifDoNotUnderstand._4
            ),
            Qualification(
              QualificationKey.DoNotCare,
              countsQualifDoNotCare._1,
              countsQualifDoNotCare._2,
              countsQualifDoNotCare._3,
              countsQualifDoNotCare._4
            ),
            Qualification(
              QualificationKey.NoOpinion,
              countsQualifNoOpinion._1,
              countsQualifNoOpinion._2,
              countsQualifNoOpinion._3,
              countsQualifNoOpinion._4
            )
          )
        ),
        Vote(
          key = VoteKey.Disagree,
          count = countsDisagree._1,
          countVerified = countsDisagree._2,
          countSequence = countsDisagree._3,
          countSegment = countsDisagree._4,
          qualifications = Seq(
            Qualification(
              QualificationKey.NoWay,
              countsQualifNoWay._1,
              countsQualifNoWay._2,
              countsQualifNoWay._3,
              countsQualifNoWay._4
            ),
            Qualification(
              QualificationKey.Impossible,
              countsQualifImpossible._1,
              countsQualifImpossible._2,
              countsQualifImpossible._3,
              countsQualifImpossible._4
            ),
            Qualification(
              QualificationKey.PlatitudeDisagree,
              countsQualifPlatitudeDisagree._1,
              countsQualifPlatitudeDisagree._2,
              countsQualifPlatitudeDisagree._3,
              countsQualifPlatitudeDisagree._4
            )
          )
        )
      )
  }

  def genProposal(question: Question, users: Seq[User], tagsIds: Seq[TagId]): Gen[Proposal] =
    for {
      content         <- CustomGenerators.LoremIpsumGen.sentence(maxLength = BusinessConfig.defaultProposalMaxLength)
      author          <- Gen.oneOf(users.map(_.userId))
      status          <- Gen.oneOf(ProposalStatus.statusMap.values.toSeq)
      refusalReason   <- CustomGenerators.LoremIpsumGen.word
      tags            <- Gen.someOf(tagsIds)
      votes           <- genProposalVotes
      organisationIds <- Gen.someOf(users.filter(_.userType == UserType.UserTypeOrganisation).map(_.userId))
      date            <- Gen.option(Gen.calendar.map(_.toZonedDateTime))
      initialProposal <- Gen.frequency((9, false), (1, true))
      slug = SlugHelper(content)
    } yield
      Proposal(
        proposalId = idGenerator.nextProposalId(),
        slug = slug,
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
