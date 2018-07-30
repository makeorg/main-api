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
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{Role, User}
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.Future

object MakeEuropeData extends InsertFixtureData {
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
        country = MakeEuropeOperation.countryConfigurations.head.country,
        language = MakeEuropeOperation.defaultLanguage,
        profile = Profile.parseProfile(dateOfBirth = Some(LocalDate.now.minusYears(age))),
        createdAt = Some(DateHelper.now())
      )

    val users = Seq(
      agedUser("yopmail+harry@make.org", "Harry", 38),
      agedUser("yopmail+hazel@make.org", "Hazel", 19),
      agedUser("yopmail+jack@make.org", "Jack", 27),
      agedUser("yopmail+george@make.org", "George", 54),
      agedUser("yopmail+abby@make.org", "Abby", 28)
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
      mayBeOperation <- api.operationService.findOneBySlug(MakeEuropeOperation.operationSlug)
    } yield
      mayBeOperation match {
        case Some(operation) =>
          operationId = operation.operationId
          localRequestContext = RequestContext.empty.copy(operationId = Some(operationId))
        case None =>
          throw new IllegalStateException(s"Unable to find an operation with slug ${MakeEuropeOperation.operationSlug}")
      }
  }

  override def extractDataLine(line: String): Option[InsertFixtureData.FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, tags) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = None,
            operation = Some(operationId),
            tags = tags.split('|').toSeq.map(TagId.apply),
            labels = Seq.empty,
            country = Country("GB"),
            language = Language("en")
          )
        )
      case _ => None
    }
  }

  override val dataResource: String = "fixtures/proposals_make-europe.csv"
  override val runInProduction: Boolean = false
}
