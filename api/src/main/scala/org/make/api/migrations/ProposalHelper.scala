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
import org.make.api.MakeApi
import org.make.api.migrations.ProposalHelper.{FixtureDataLine, UserInfo}
import org.make.api.proposal.{AcceptProposalCommand, ProposeCommand}
import org.make.api.user.UserRegisterData
import org.make.core.idea.Idea
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.proposal.{ProposalId, SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.question.Question
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Random
import scala.util.matching.Regex

trait ProposalHelper {

  def users: Seq[UserInfo]

  val EmailRegex: Regex = """$yopmail\+([^@]+)@make\.org^""".r

  val emptyContext: RequestContext = RequestContext.empty.copy(source = Some("core"))
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

  implicit def executor: ExecutionContext
  val random = new Random(System.nanoTime())

  def extractFirstNameFromEmail(email: String): String = {
    email match {
      case EmailRegex(name) => name
      case other            => other.substring(0, other.indexOf("@"))
    }
  }

  def userAge(email: String): Int = {
    20 + Math.abs(email.hashCode) % 60
  }

  def getUser(api: MakeApi, email: String): Future[User] = {
    val search: Future[Option[User]] = if (email.matches("yopmail\\+an[n]?onymous@make\\.org")) {
      Future.successful(None)
    } else {
      api.userService.getUserByEmail(email)
    }

    search.flatMap {
      case Some(user) => Future.successful(user)
      case None =>
        val maybeUserSpec: Option[UserInfo] = users
          .filter(!_.email.matches("yopmail\\+an[n]?onymous@make\\.org"))
          .find(_.email == email)

        maybeUserSpec
          .map(
            userSpec =>
              api.userService.register(
                UserRegisterData(
                  email = userSpec.email,
                  firstName = Some(userSpec.firstName),
                  lastName = None,
                  password = None,
                  lastIp = None,
                  dateOfBirth = None,
                  profession = None,
                  postalCode = None,
                  country = userSpec.country,
                  language = userSpec.language
                ),
                requestContext = emptyContext
            )
          )
          .getOrElse(getUser(api, users(random.nextInt(users.size)).email))
    }
  }

  def insertProposal(api: MakeApi, content: String, email: String, question: Question): Future[ProposalId] = {
    getUser(api, email).flatMap { user =>
      api.proposalCoordinatorService.propose(
        ProposeCommand(
          proposalId = api.idGenerator.nextProposalId(),
          requestContext = emptyContext.copy(
            operationId = question.operationId,
            currentTheme = question.themeId,
            country = Some(question.country),
            language = Some(question.language),
          ),
          user = user,
          createdAt = DateHelper.now(),
          content = content,
          question = question
        )
      )
    }
  }

  def insertIfNeededProposal(api: MakeApi, content: String, email: String, question: Question): Future[ProposalId] = {
    api.elasticsearchProposalAPI
      .searchProposals(SearchQuery(filters = Some(SearchFilters(slug = Some(SlugSearchFilter(SlugHelper(content)))))))
      .flatMap {
        case ProposalsSearchResult(total, results) if total > 0 => Future.successful(results.head.id)
        case _                                                  => insertProposal(api, content, email, question)
      }
  }

  def getOrCreateIdea(api: MakeApi, name: String, question: Question): Future[Idea] = {
    api.ideaService.fetchOneByName(name).flatMap {
      case Some(idea) => Future.successful(idea)
      case None =>
        api.ideaService.insert(name, question)
    }
  }

  def acceptProposal(api: MakeApi,
                     proposalId: ProposalId,
                     ideaName: String,
                     question: Question,
                     tagIds: Seq[TagId],
                     labels: Seq[LabelId]): Future[Unit] = {

    getOrCreateIdea(api, ideaName, question).flatMap { idea =>
      api.proposalCoordinatorService.accept(
        AcceptProposalCommand(
          moderator = moderatorId,
          proposalId = proposalId,
          requestContext = emptyContext,
          sendNotificationEmail = false,
          newContent = None,
          question = question,
          labels = labels,
          tagIds,
          idea.ideaId
        )
      )
    }.map(_ => ())

  }

  def readProposalFile(dataResource: String): Seq[FixtureDataLine] = {
    Source.fromResource(dataResource).getLines().toSeq.drop(1).flatMap(extractDataLine)
  }

  def extractDataLine(line: String): Option[FixtureDataLine]

}

object ProposalHelper {
  case class FixtureDataLine(email: String,
                             content: String,
                             theme: Option[ThemeId],
                             operation: Option[OperationId],
                             tags: Seq[String],
                             labels: Seq[LabelId],
                             country: Country,
                             language: Language,
                             acceptProposal: Boolean)

  case class UserInfo(email: String, firstName: String, age: Int, country: Country, language: Language)
}
