package org.make.api.migrations
import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.api.migrations.InsertFixtureData.{FixtureDataLine, ProposalToAccept}
import org.make.api.proposal.ValidateProposalRequest
import org.make.core.operation.OperationId
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.reference.{LabelId, TagId, ThemeId}
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
            verified = true,
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

  case class UserMinimalData(email: String, country: String, language: String)

  override def migrate(api: MakeApi): Future[Unit] = {
    val csv: Seq[FixtureDataLine] =
      Source.fromResource(dataResource).getLines().toSeq.drop(1).flatMap(extractDataLine)

    val users = createUsers(csv, api)

    val proposalsToAccept = csv.map { line =>
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
                  api.ideaService.insert(
                    name = proposalsToAccept.content,
                    language = Some(proposalsToAccept.language),
                    country = Some(proposalsToAccept.country),
                    operationId = proposalsToAccept.operation,
                    question = None,
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
                             country: String,
                             language: String)

  case class ProposalToAccept(content: String,
                              country: String,
                              language: String,
                              tags: Seq[TagId],
                              labels: Seq[LabelId],
                              theme: Option[ThemeId],
                              operation: Option[OperationId],
                              userEmail: String)
}