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
import java.util.concurrent.Executors

import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.{CountryConfiguration, SequenceWithCountryLanguage}
import org.make.api.proposal.ValidateProposalRequest
import org.make.core.idea.Idea
import org.make.core.operation.{OperationCountryConfiguration, OperationId, OperationTranslation}
import org.make.core.profile.Profile
import org.make.core.proposal.ProposalStatus.{Accepted, Pending}
import org.make.core.proposal._
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.sequence.SequenceStatus
import org.make.core.tag.Tag
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext, SlugHelper}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.Source
import scala.util.Random

object HuffingPostOperations extends Migration {

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")
  var huffingPostUser: Seq[User] = Seq.empty

  val operations: Map[String, CreateOperation.CountryConfiguration] = Map(
    "politique-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Politique",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "economie-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Économie",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "international-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "International",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "culture-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Culture",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "ecologie-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Écologie",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "societe-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Société",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    ),
    "education-huffpost" -> CountryConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      title = "Éducation",
      startDate = LocalDate.parse("2018-08-06"),
      endDate = Some(LocalDate.parse("2020-08-06")),
      tags = Seq.empty
    )
  )

  val operationsTagsSource: Map[String, String] = Map(
    "politique-huffpost" -> "fixtures/huffingpost/tags_politique.csv",
    "economie-huffpost" -> "fixtures/huffingpost/tags_economie.csv",
    "international-huffpost" -> "fixtures/huffingpost/tags_international.csv",
    "culture-huffpost" -> "fixtures/huffingpost/tags_culture.csv",
    "ecologie-huffpost" -> "fixtures/huffingpost/tags_ecologie.csv",
    "societe-huffpost" -> "fixtures/huffingpost/tags_societe.csv",
    "education-huffpost" -> "fixtures/huffingpost/tags_education.csv"
  )

  val operationsProposalsSource: Map[String, String] = Map(
    "politique-huffpost" -> "fixtures/huffingpost/proposals_politique.csv",
    "economie-huffpost" -> "fixtures/huffingpost/proposals_economie.csv",
    "international-huffpost" -> "fixtures/huffingpost/proposals_international.csv",
    "culture-huffpost" -> "fixtures/huffingpost/proposals_culture.csv",
    "ecologie-huffpost" -> "fixtures/huffingpost/proposals_ecologie.csv",
    "societe-huffpost" -> "fixtures/huffingpost/proposals_societe.csv",
    "education-huffpost" -> "fixtures/huffingpost/proposals_education.csv"
  )

  val defaultLanguage: Language = Language("fr")

  override def initialize(api: MakeApi): Future[Unit] = createUsers(api)

  override def migrate(api: MakeApi): Future[Unit] = {

    sequentially(operations.toSeq) {
      case (operationSlug, countryConfiguration) =>
        // create Sequence and Operation
        retryableFuture(
          api.sequenceService
            .create(
              userId = moderatorId,
              requestContext = emptyContext,
              createdAt = DateHelper.now(),
              title = operationSlug,
              themeIds = Seq.empty,
              operationId = None,
              searchable = true
            )
            .map(_.get)
            .map { sequenceResponse =>
              SequenceWithCountryLanguage(
                sequence = sequenceResponse,
                country = countryConfiguration.country,
                language = countryConfiguration.language
              )
            }
        ).map { sequenceWithCountryLanguage =>
          api.operationService
            .findOneBySlug(operationSlug)
            .flatMap {
              case Some(operation) => Future.successful(operation.operationId)
              case None =>
                api.operationService
                  .create(
                    userId = moderatorId,
                    slug = operationSlug,
                    defaultLanguage = defaultLanguage,
                    translations = Seq(
                      OperationTranslation(
                        title = countryConfiguration.title,
                        language = sequenceWithCountryLanguage.language
                      )
                    ),
                    countriesConfiguration = Seq(
                      OperationCountryConfiguration(
                        countryCode = sequenceWithCountryLanguage.country,
                        tagIds = countryConfiguration.tags,
                        landingSequenceId = sequenceWithCountryLanguage.sequence.sequenceId,
                        startDate = Some(countryConfiguration.startDate),
                        endDate = countryConfiguration.endDate
                      )
                    )
                  )
            }
            .map { operationId =>
              retryableFuture(
                api.sequenceService.update(
                  sequenceId = sequenceWithCountryLanguage.sequence.sequenceId,
                  moderatorId = moderatorId,
                  requestContext = emptyContext,
                  title = None,
                  status = Some(SequenceStatus.Published),
                  operationId = Some(operationId),
                  themeIds = Seq.empty
                )
              )

              val tags: Seq[ImportTagsData.TagsDataLine] =
                Source
                  .fromResource(operationsTagsSource(operationSlug))
                  .getLines()
                  .toSeq
                  .drop(1)
                  .flatMap(ImportTagsData.extractDataLine)

              // create Tags of the operation
              api.tagService
                .findByOperationId(operationId)
                .flatMap { foundTags =>
                  if (foundTags.nonEmpty) {
                    Future.successful(foundTags)
                  } else {
                    Future
                      .traverse(tags) { tag =>
                        api.tagService
                          .createTag(
                            label = tag.label,
                            tagTypeId = tag.tagTypeId,
                            operationId = Some(operationId),
                            themeId = None,
                            country = tag.country,
                            language = tag.language,
                            display = tag.tagDisplay,
                            weight = tag.weight
                          )
                      }
                      .map { createdTags =>
                        createdTags
                      }
                  }
                }
                .map { operationTags =>
                  val proposalsToInsert: Seq[ProposalsDataLine] =
                    Source
                      .fromResource(operationsProposalsSource(operationSlug))
                      .getLines()
                      .toSeq
                      .drop(1)
                      .flatMap(extractProposalDataLine)

                  // Traverse the proposals insert and validate theme
                  sequentially(proposalsToInsert) { proposalToInsert =>
                    api.elasticsearchProposalAPI
                      .countProposals(
                        SearchQuery(
                          filters = Some(
                            SearchFilters(
                              slug = Some(SlugSearchFilter(SlugHelper(proposalToInsert.content))),
                              status = Some(StatusSearchFilter(Seq(Accepted, Pending))),
                              operation = Some(OperationSearchFilter(operationId))
                            )
                          )
                        )
                      )
                      .flatMap { countResult =>
                        if (countResult > 0) {
                          Future.successful {}
                        } else {
                          val tagsOfProposal: Seq[Tag] =
                            operationTags.filter(tag => proposalToInsert.tagsLabel.contains(tag.label))

                          for {
                            user <- retryableFuture(retrieveOrInsertUser(api = api, email = proposalToInsert.email))
                            idea <- retryableFuture(
                              retrieveOrInsertIdea(
                                api = api,
                                name = proposalToInsert.content,
                                language = proposalToInsert.language,
                                country = proposalToInsert.country,
                                operationId = Some(operationId),
                                themeId = None
                              )
                            )
                            proposalId <- retryableFuture(
                              api.proposalService
                                .propose(
                                  user,
                                  RequestContext.empty.copy(
                                    question = Some(operationSlug),
                                    source = Some("huffingpost"),
                                    operationId = Some(operationId)
                                  ),
                                  DateHelper.now(),
                                  proposalToInsert.content,
                                  Some(operationId),
                                  None,
                                  Some(proposalToInsert.language),
                                  Some(proposalToInsert.country)
                                )
                            )
                            _ <- retryableFuture(
                              api.proposalService.validateProposal(
                                proposalId,
                                moderatorId,
                                RequestContext.empty.copy(
                                  question = Some(operationSlug),
                                  source = Some("huffingpost"),
                                  operationId = Some(operationId)
                                ),
                                ValidateProposalRequest(
                                  newContent = None,
                                  sendNotificationEmail = false,
                                  theme = None,
                                  labels = Seq.empty,
                                  tags = tagsOfProposal.map(_.tagId),
                                  similarProposals = Seq.empty,
                                  operation = Some(operationId),
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

      case _ => Future.failed(new RuntimeException("Error When importingHuffingPost operations"))
    }
  }

  def retrieveOrInsertIdea(api: MakeApi,
                           name: String,
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

  def retrieveOrInsertUser(api: MakeApi, email: String): Future[User] = {
    val annonymousEmail: String = "yopmail+annonymous@make.org"

    // If annonymous is the user proposal pick a random yopmail user
    if (annonymousEmail.equals(email)) {
      Future.successful(huffingPostUser(Random.nextInt(huffingPostUser.size)))
    } else {
      api.persistentUserService.findByEmail(email).flatMap {
        case Some(user) => Future.successful(user)
        case None       => Future.successful(huffingPostUser(Random.nextInt(huffingPostUser.size)))
      }
    }
  }

  def createUsers(api: MakeApi) = {
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
        country = Country("FR"),
        language = defaultLanguage,
        profile = Profile.parseProfile(dateOfBirth = Some(LocalDate.now.minusYears(age))),
        createdAt = Some(DateHelper.now())
      )

    val usersToInsert = Seq(
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
      agedUser("yopmail+lucas@make.org", "Lucas", 18),
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

    sequentially(usersToInsert) { user =>
      api.persistentUserService
        .findByEmail(user.email)
        .flatMap {
          case Some(u) =>
            huffingPostUser ++= Seq(u)
            Future.successful {}
          case None => api.persistentUserService.persist(user).map(u => huffingPostUser ++= Seq(u))
        }

    }.recoverWith {
      case _ => Future.successful(())
    }
  }

  def extractProposalDataLine(line: String): Option[ProposalsDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, tagsString) =>
        val tagsLabel: Seq[String] = tagsString.split('|').toSeq.map(_.trim)

        Some(
          ProposalsDataLine(
            email = email,
            content = content,
            tagsLabel = tagsLabel,
            country = Country("FR"),
            language = Language("fr")
          )
        )
      case _ => None
    }
  }

  override val runInProduction: Boolean = true
}

final case class ProposalsDataLine(email: String,
                                   content: String,
                                   tagsLabel: Seq[String],
                                   country: Country,
                                   language: Language)
