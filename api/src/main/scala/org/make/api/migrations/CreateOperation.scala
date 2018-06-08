package org.make.api.migrations

import java.time.LocalDate
import java.util.concurrent.Executors

import org.make.api.MakeApi
import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.api.sequence.SequenceResponse
import org.make.core.operation.{OperationCountryConfiguration, OperationTranslation}
import org.make.core.sequence.SequenceStatus
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait CreateOperation extends Migration {

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val emptyContext: RequestContext = RequestContext.empty
  val moderatorId = UserId("11111111-1111-1111-1111-111111111111")

  def operationSlug: String
  def defaultLanguage: String

  def countryConfigurations: Seq[CountryConfiguration]

  case class SequenceWithCountryLanguage(sequence: SequenceResponse, country: String, language: String)

  private def findConfiguration(country: String, language: String): CountryConfiguration = {
    countryConfigurations
      .find(configuration => configuration.country == country && configuration.language == language)
      .getOrElse(CountryConfiguration(country, language, Seq.empty, "", startDate = LocalDate.now(), endDate = None))
  }

  override def migrate(api: MakeApi): Future[Unit] = {

    api.operationService.findOneBySlug(operationSlug).flatMap {
      case Some(_) => Future.successful {}
      case None =>
        Future
          .traverse(countryConfigurations) { configuration =>
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
                .map(response => SequenceWithCountryLanguage(response, configuration.country, configuration.language))
            )
          }
          .flatMap { sequenceResponses =>
            retryableFuture(
              api.operationService
                .create(
                  userId = moderatorId,
                  slug = operationSlug,
                  defaultLanguage = defaultLanguage,
                  translations = sequenceResponses.map { response =>
                    val configuration = findConfiguration(response.country, response.language)
                    OperationTranslation(title = configuration.title, language = response.language)
                  },
                  countriesConfiguration = sequenceResponses.map { response =>
                    val configuration = findConfiguration(response.country, response.language)
                    OperationCountryConfiguration(
                      countryCode = response.country,
                      tagIds = configuration.tags,
                      landingSequenceId = response.sequence.sequenceId,
                      startDate = Some(configuration.startDate),
                      endDate = configuration.endDate
                    )
                  }
                )
                .map { operationId =>
                  sequenceResponses.map { sequence =>
                    api.sequenceService.update(
                      sequenceId = sequence.sequence.sequenceId,
                      moderatorId = moderatorId,
                      requestContext = emptyContext,
                      title = None,
                      status = Some(SequenceStatus.Published),
                      operationId = Some(operationId),
                      themeIds = Seq.empty
                    )

                    val configuration: CountryConfiguration = findConfiguration(sequence.country, sequence.language)
                    Future
                      .traverse(configuration.tags) { tag =>
                        retryableFuture(
                          api.tagService.getTag(tag).map { maybeFoundTag =>
                            maybeFoundTag.map { foundTag =>
                              api.tagService.updateTag(
                                tagId = foundTag.tagId,
                                label = foundTag.label,
                                display = foundTag.display,
                                tagTypeId = foundTag.tagTypeId,
                                weight = foundTag.weight,
                                operationId = Some(operationId),
                                themeId = None,
                                country = sequence.country,
                                language = sequence.language
                              )
                            }
                          }
                        )
                      }
                  }
                }
            )
          }
    }
  }
}

object CreateOperation {
  final case class CountryConfiguration(country: String,
                                        language: String,
                                        tags: Seq[TagId],
                                        title: String,
                                        startDate: LocalDate,
                                        endDate: Option[LocalDate])
}
