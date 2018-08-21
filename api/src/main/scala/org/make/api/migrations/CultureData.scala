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
import org.make.core.user.{Role, User}
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.Future
import scala.io.Source

object CultureData extends InsertFixtureData {
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
        country = CultureOperation.countryConfigurations.headOption.map(_.country).getOrElse(Country("FR")),
        language = CultureOperation.defaultLanguage,
        profile = Profile.parseProfile(dateOfBirth = Some(LocalDate.now.minusYears(age))),
        createdAt = Some(DateHelper.now())
      )

    val users = Seq(
      agedUser("yopmail+leila@make.org", "Leila", 33),
      agedUser("yopmail+ariane@make.org", "Ariane", 19),
      agedUser("yopmail+aminata@make.org", "Aminata", 45),
      agedUser("yopmail+josephine@make.org", "Joséphine", 54),
      agedUser("yopmail+joao@make.org", "Joao", 48),
      agedUser("yopmail+isaac@make.org", "Isaac", 38),
      agedUser("yopmail+pierre-marie@make.org", "Pierre-Marie", 50),
      agedUser("yopmail+chen@make.org", "Chen", 17),
      agedUser("yopmail+lucas@make.org", "Lucas", 23),
      agedUser("yopmail+elisabeth@make.org", "Elisabeth", 36),
      agedUser("yopmail+jordi@make.org", "Jordi", 30),
      agedUser("yopmail+sophie@make.org", "Sophie", 39),
      agedUser("yopmail+alek@make.org", "Alek", 21),
      agedUser("yopmail+elisabeth@make.org", "Elisabeth", 65),
      agedUser("yopmail+lucas@make.org", "Lucas", 18)
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
      maybeOperation <- api.operationService.findOneBySlug(CultureOperation.operationSlug)
    } yield
      maybeOperation match {
        case Some(operation) =>
          operationId = operation.operationId
          localRequestContext = RequestContext.empty.copy(
            question = Some("Comment rendre la culture accessible à tous?"),
            source = Some("core"),
            operationId = Some(operationId)
          )
        case None =>
          throw new IllegalStateException(s"Unable to find an operation with slug ${CultureOperation.operationSlug}")
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
                api.operationService.findOneBySlug(CultureOperation.operationSlug).map(_.get.operationId)
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
      case Array(email, content, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = None,
            operation = Some(operationId),
            tags = Seq.empty,
            labels = Seq.empty,
            country = Country(country),
            language = Language(language)
          )
        )
      case _ => None
    }
  }

  override val dataResource: String = "fixtures/proposals_culture.csv"
  override val runInProduction: Boolean = true
}
