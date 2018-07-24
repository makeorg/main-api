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
import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.api.migrations.InsertFixtureData.{FixtureDataLine, ProposalToAccept}
import org.make.api.proposal.ValidateProposalRequest
import org.make.core.idea.Idea
import org.make.core.operation.OperationId
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.Source
import scala.util.matching.Regex

trait InsertFixtureData extends Migration with StrictLogging {

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")
  val EmailRegex: Regex = """$yopmail\+([^@]+)@make\.org^""".r

  def extractDataLine(line: String): Option[FixtureDataLine]

  def dataResource: String

  def createUsers(csv: Seq[InsertFixtureData.FixtureDataLine], api: MakeApi): Seq[User] = {
    var emails = Set.empty[String]

    csv.flatMap { line =>
      if (emails.contains(line.email)) {
        None
      } else {
        emails += line.email
        val firstName = line.email match {
          case EmailRegex(name) => name
          case _                => "anonymous"
        }

        Some(
          User(
            userId = api.idGenerator.nextUserId(),
            email = line.email,
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
            country = line.country,
            language = line.language,
            profile = None,
            createdAt = Some(DateHelper.now())
          )
        )

      }
    }
  }

  def requestContext: RequestContext

  case class UserMinimalData(email: String, country: Country, language: Language)

  override def migrate(api: MakeApi): Future[Unit] = {

    def retrieveOrInsertIdea(name: String,
                             language: Language,
                             country: Country,
                             operationId: Option[OperationId],
                             themeId: Option[ThemeId]): Future[Idea] = {
      api.ideaService.fetchOneByName(name).flatMap {
        case Some(idea) => Future.successful(idea)
        case None =>
          api.ideaService.insert(
            name = name,
            language = Some(language),
            country = Some(country),
            operationId = operationId,
            question = None,
            themeId = themeId
          )
      }
    }

    val csv: Seq[FixtureDataLine] =
      Source.fromResource(dataResource).getLines().toSeq.drop(1).flatMap(extractDataLine)

    val users = createUsers(csv, api)

    val proposalsToAccept: Seq[ProposalToAccept] = csv.map { line =>
      ProposalToAccept(
        line.content,
        line.country,
        line.language,
        line.tags,
        line.labels,
        line.theme,
        line.operation,
        line.email
      )
    }

    sequentially(users) { user =>
      api.persistentUserService
        .findByEmail(user.email)
        .flatMap {
          case Some(_) => Future.successful {}
          case None    => api.persistentUserService.persist(user).map(_ => ())
        }

    }.recoverWith {
      case _ => Future.successful(())
    }.flatMap { _ =>
      sequentially(proposalsToAccept) { proposalsToAccept =>
        api.elasticsearchProposalAPI
          .countProposals(
            SearchQuery(
              filters = Some(SearchFilters(slug = Some(SlugSearchFilter(SlugHelper(proposalsToAccept.content)))))
            )
          )
          .flatMap { countResult =>
            if (countResult > 0) {
              Future.successful {}
            } else {
              for {
                user <- retryableFuture(api.persistentUserService.findByEmail(proposalsToAccept.userEmail)).map(_.get)
                idea <- retryableFuture(
                  retrieveOrInsertIdea(
                    name = proposalsToAccept.content,
                    language = proposalsToAccept.language,
                    country = proposalsToAccept.country,
                    operationId = proposalsToAccept.operation,
                    themeId = proposalsToAccept.theme
                  )
                )
                proposalId <- retryableFuture(
                  api.proposalService
                    .propose(
                      user,
                      requestContext,
                      DateHelper.now(),
                      proposalsToAccept.content,
                      proposalsToAccept.operation,
                      proposalsToAccept.theme,
                      Some(proposalsToAccept.language),
                      Some(proposalsToAccept.country)
                    )
                )
                _ <- retryableFuture(
                  api.proposalService.validateProposal(
                    proposalId,
                    moderatorId,
                    requestContext,
                    ValidateProposalRequest(
                      newContent = None,
                      sendNotificationEmail = false,
                      theme = proposalsToAccept.theme,
                      labels = proposalsToAccept.labels,
                      tags = proposalsToAccept.tags,
                      similarProposals = Seq.empty,
                      operation = proposalsToAccept.operation,
                      idea = Some(idea.ideaId)
                    )
                  )
                )
              } yield {}
            }
          }
      }
    }
  }

}

object InsertFixtureData {
  case class FixtureDataLine(email: String,
                             content: String,
                             theme: Option[ThemeId],
                             operation: Option[OperationId],
                             tags: Seq[TagId],
                             labels: Seq[LabelId],
                             country: Country,
                             language: Language)

  case class ProposalToAccept(content: String,
                              country: Country,
                              language: Language,
                              tags: Seq[TagId],
                              labels: Seq[LabelId],
                              theme: Option[ThemeId],
                              operation: Option[OperationId],
                              userEmail: String)
}
