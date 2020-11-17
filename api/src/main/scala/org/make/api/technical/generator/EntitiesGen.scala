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

import cats.data.NonEmptyList
import org.make.api.operation.CreateOperationOfQuestion
import org.make.api.technical.{DefaultIdGeneratorComponent, IdGeneratorComponent}
import org.make.api.user.UserRegisterData
import org.make.core.operation.OperationId
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.question.QuestionId
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.make.core.DateHelper._
import org.make.core.technical.generator.{EntitiesGen => CoreEntitiesGen}
import enumeratum.values.scalacheck._
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.MaxSize
import eu.timepit.refined.{refineV, W}
import org.make.api.organisation.OrganisationRegisterData
import org.make.api.partner.CreatePartnerRequest
import org.make.core.partner.PartnerKind
import org.make.core.technical.generator.CustomGenerators
import org.make.core.user.User

trait EntitiesGen extends CoreEntitiesGen { self: IdGeneratorComponent =>

  def genCreateOperationOfQuestion(operationId: OperationId): Gen[CreateOperationOfQuestion] =
    for {
      date                 <- Gen.calendar.map(_.toZonedDateTime)
      operationTitle       <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(150))
      shortTitle           <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(30))
      slug                 <- CustomGenerators.LoremIpsumGen.slug(maxLength = Some(30))
      (country, language)  <- genCountryLanguage
      question             <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(150))
      consultationImage    <- CustomGenerators.ImageUrl.gen(width = 300, height = 100)
      consultationImageAlt <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(130))
      descriptionImage     <- CustomGenerators.ImageUrl.gen(width = 300, height = 100)
      descriptionImageAlt  <- CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(130))
    } yield CreateOperationOfQuestion(
      operationId = operationId,
      startDate = date,
      endDate = date.plusMonths(3),
      operationTitle = operationTitle,
      slug = slug,
      countries = NonEmptyList.of(country),
      language = language,
      question = question,
      shortTitle = Some(shortTitle),
      consultationImage = Some(consultationImage),
      consultationImageAlt = Some(refineV[MaxSize[W.`130`.T]](consultationImageAlt).getOrElse("")),
      descriptionImage = Some(descriptionImage),
      descriptionImageAlt = Some(refineV[MaxSize[W.`130`.T]](descriptionImageAlt).getOrElse("")),
      actions = None
    )

  def genUserRegisterData(questionId: Option[QuestionId]): Gen[UserRegisterData] =
    for {
      email                     <- CustomGenerators.Mail.gen()
      firstName                 <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      lastName                  <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      dateOfBirth               <- Gen.option(Gen.calendar.map(_.toZonedDateTime.toLocalDate))
      profession                <- Gen.option(CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(15)))
      postalCode                <- Gen.option(CustomGenerators.PostalCode.gen)
      gender                    <- Gen.option(arbitrary[Gender])
      socioProfessionalCategory <- Gen.option(arbitrary[SocioProfessionalCategory])
      (country, _)              <- genCountryLanguage
      optIn                     <- Gen.option(Arbitrary.arbitrary[Boolean])
      optInPartner              <- Gen.option(Arbitrary.arbitrary[Boolean])
      roles                     <- genRoles
      politicalParty            <- Gen.option(CustomGenerators.LoremIpsumGen.word)
      publicProfile             <- Arbitrary.arbitrary[Boolean]
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
      questionId = questionId,
      optIn = optIn,
      optInPartner = optInPartner,
      roles = roles,
      availableQuestions = questionId.toList,
      politicalParty = politicalParty,
      website = None,
      publicProfile = publicProfile
    )

  def genOrganisationRegisterData: Gen[OrganisationRegisterData] =
    for {
      name         <- CustomGenerators.LoremIpsumGen.word
      email        <- CustomGenerators.Mail.gen(Some("organisation"))
      avatar       <- CustomGenerators.ImageUrl.gen(width = 100, height = 100)
      description  <- Gen.option(CustomGenerators.LoremIpsumGen.sentence(maxLength = Some(300)))
      (country, _) <- genCountryLanguage
      website      <- Gen.option(CustomGenerators.URL.gen)
    } yield OrganisationRegisterData(
      name = name,
      email = email,
      password = Some(email),
      avatar = Some(avatar),
      description = description,
      country = country,
      website = website
    )

  def genCreatePartnerRequest(orga: Option[User], questionId: QuestionId): Gen[CreatePartnerRequest] =
    for {
      name <- CustomGenerators.LoremIpsumGen.word
      logo <- CustomGenerators.ImageUrl.gen(width = 100, height = 100)
      link <- CustomGenerators.URL.gen
      partnerKind <- Gen.frequency(
        (2, PartnerKind.Media),
        (7, PartnerKind.ActionPartner),
        (1, PartnerKind.Founder),
        (4, PartnerKind.Actor)
      )
      weight <- Gen.posNum[Float]
    } yield CreatePartnerRequest(
      name = orga.flatMap(_.displayName).getOrElse(name),
      logo = orga.flatMap(_.profile.flatMap(_.avatarUrl)).orElse(Some(logo)),
      link = orga.flatMap(_.profile.flatMap(_.website)).orElse(Some(link)),
      organisationId = orga.map(_.userId),
      partnerKind = partnerKind,
      questionId = questionId,
      weight = weight
    )

}

object EntitiesGen extends EntitiesGen with DefaultIdGeneratorComponent
