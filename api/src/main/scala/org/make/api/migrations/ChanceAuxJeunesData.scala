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

package org.make.api.migrations

import java.time.LocalDate

import org.make.api.MakeApi
import org.make.api.migrations.InsertFixtureData.FixtureDataLine
import org.make.core.operation.OperationId
import org.make.core.profile.Profile
import org.make.core.proposal.ProposalStatus.{Accepted, Pending}
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter, StatusSearchFilter}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{Role, User}
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.Future
import scala.io.Source

object ChanceAuxJeunesData extends InsertFixtureData {
  var operationId: OperationId = _
  var localRequestContext: RequestContext = _
  override def requestContext: RequestContext = localRequestContext

  private def createUsers(api: MakeApi) = {
    def agedUser(email: String, firstName: String, age: Int): User =
      User(
        userId = api.idGenerator.nextUserId(),
        email = email,
        firstName = Some(firstName),
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleCitizen),
        country = ChanceAuxJeunesOperation.countryConfigurations.headOption.map(_.country).getOrElse(Country("FR")),
        language = ChanceAuxJeunesOperation.defaultLanguage,
        profile = Profile.parseProfile(dateOfBirth = Some(LocalDate.now.minusYears(age))),
        createdAt = Some(DateHelper.now())
      )

    val users = Seq(
      agedUser("yopmail+sandrine@make.org", "Sandrine", 35),
      agedUser("yopmail+corinne@make.org", "Corinne", 52),
      agedUser("yopmail+julie@make.org", "Julie", 18),
      agedUser("yopmail+lionel@make.org", "Lionel", 25),
      agedUser("yopmail+jean@make.org", "Jean", 48),
      agedUser("yopmail+odile@make.org", "Odile", 29),
      agedUser("yopmail+nicolas@make.org", "Nicolas", 42),
      agedUser("yopmail+jamel@make.org", "Jamel", 22),
      agedUser("yopmail+laurene@make.org", "Laurène", 27),
      agedUser("yopmail+françois@make.org", "François", 33),
      agedUser("yopmail+aissatou@make.org", "Aissatou", 31),
      agedUser("yopmail+eric@make.org", "Éric", 56),
      agedUser("yopmail+sylvain@make.org", "Sylvain", 46)
    )

    sequentially(users) { user =>
      api.persistentUserService
        .findByEmail(user.email)
        .flatMap {
          case Some(_) => Future.successful {}
          case None    => api.persistentUserService.persist(user).map(_ => ())
        }

    }.recoverWith {
      case _ => Future.successful(())
    }
  }

  override def initialize(api: MakeApi): Future[Unit] = {
    for {
      _              <- createUsers(api)
      maybeOperation <- api.operationService.findOneBySlug(ChanceAuxJeunesOperation.operationSlug)
    } yield
      maybeOperation match {
        case Some(operation) =>
          operationId = operation.operationId
          localRequestContext = RequestContext.empty.copy(
            question = Some("Comment donner une chance à chaque jeune ?"),
            source = Some("core"),
            operationId = Some(operationId)
          )
        case None =>
          throw new IllegalStateException(
            s"Unable to find an operation with slug ${ChanceAuxJeunesOperation.operationSlug}"
          )
      }
  }

  case class ProposalToInsert(content: String, country: Country, language: Language, userEmail: String)

  override def migrate(api: MakeApi): Future[Unit] = {
    val csv: Seq[FixtureDataLine] =
      Source.fromResource(dataResource).getLines().toSeq.drop(1).flatMap(extractDataLine)

    val proposalsToInsert = csv.map { line =>
      ProposalToInsert(line.content, line.country, line.language, line.email)
    }

    sequentially(proposalsToInsert) { proposalsToInsert =>
      api.elasticsearchProposalAPI
        .countProposals(
          SearchQuery(
            filters = Some(
              SearchFilters(
                slug = Some(SlugSearchFilter(SlugHelper(proposalsToInsert.content))),
                status = Some(StatusSearchFilter(Seq(Accepted, Pending)))
              )
            )
          )
        )
        .flatMap { countResult =>
          if (countResult > 0) {
            Future.successful {}
          } else {
            for {
              user <- retryableFuture(api.persistentUserService.findByEmail(proposalsToInsert.userEmail)).map(_.get)
              operationId <- retryableFuture(
                api.operationService.findOneBySlug(ChanceAuxJeunesOperation.operationSlug).map(_.get.operationId)
              )
              question <- api.questionService.findQuestion(
                None,
                Some(operationId),
                proposalsToInsert.country,
                proposalsToInsert.language
              )
              proposalId <- retryableFuture(
                api.proposalService
                  .propose(user, requestContext, DateHelper.now(), proposalsToInsert.content, question.get)
              )
            } yield {}
          }
        }
    }
  }

  override def extractDataLine(line: String): Option[InsertFixtureData.FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, country, language, tags) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = None,
            operation = Some(operationId),
            tags = tags.split('|').toSeq.map(TagId.apply),
            labels = Seq.empty,
            country = Country(country),
            language = Language(language)
          )
        )
      case _ => None
    }
  }

  override val dataResource: String = "fixtures/proposals_chance-aux-jeunes.csv"
  override val runInProduction: Boolean = false
}
