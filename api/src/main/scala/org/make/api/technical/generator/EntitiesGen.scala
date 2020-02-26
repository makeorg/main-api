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
import org.make.core.BusinessConfig
import org.make.core.operation.{OperationId, OperationKind, OperationStatus, SimpleOperation}
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.proposal.Proposal
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role
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

  def genProposal: Gen[Proposal] = ???
}
