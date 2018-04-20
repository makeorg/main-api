package org.make.api.migrations

import java.time.LocalDate

import org.make.api.MakeApi
import org.make.api.migrations.InsertFixtureData.FixtureDataLine
import org.make.core.operation.OperationId
import org.make.core.profile.Profile
import org.make.core.proposal.ProposalStatus.{Accepted, Pending}
import org.make.core.proposal.{SearchFilters, SearchQuery, SlugSearchFilter, StatusSearchFilter}
import org.make.core.reference.TagId
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
        verified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq(Role.RoleCitizen),
        country = MakeEuropeOperation.defaultLanguage,
        language = MakeEuropeOperation.defaultLanguage,
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

  case class ProposalToInsert(content: String, country: String, language: String, userEmail: String)

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
              proposalId <- retryableFuture(
                api.proposalService
                  .propose(
                    user,
                    requestContext,
                    DateHelper.now(),
                    proposalsToInsert.content,
                    Some(operationId),
                    None,
                    Some(proposalsToInsert.language),
                    Some(proposalsToInsert.country)
                  )
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
            country = country,
            language = language
          )
        )
      case _ => None
    }
  }

  override val dataResource: String = "fixtures/proposals_chance-aux-jeunes.csv"
  override val runInProduction: Boolean = false
}
