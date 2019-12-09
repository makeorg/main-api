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

package org.make.api.theme

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.api.theme.DefaultPersistentThemeServiceComponent.{PersistentTheme, PersistentThemeTranslation}
import org.make.core.DateHelper
import org.make.core.reference._
import org.make.core.tag.{Tag, TagId}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentThemeServiceComponent {
  def persistentThemeService: PersistentThemeService
}

trait PersistentThemeService {
  def findAll(): Future[Seq[Theme]]
  def persist(theme: Theme): Future[Theme]
  def addTranslationToTheme(translation: ThemeTranslation, theme: Theme): Future[Theme]
}

trait DefaultPersistentThemeServiceComponent extends PersistentThemeServiceComponent {
  this: MakeDBExecutionContextComponent with DefaultPersistentTagServiceComponent =>

  override lazy val persistentThemeService: PersistentThemeService = new DefaultPersistentThemeService

  class DefaultPersistentThemeService extends PersistentThemeService with ShortenedNames with StrictLogging {

    private val themeAlias = PersistentTheme.themeAlias
    private val themeTranslationAlias = PersistentThemeTranslation.themeTranslationAlias
    private val column = PersistentTheme.column
    private val themeTranslationcolumn = PersistentThemeTranslation.column

    override def findAll(): Future[Seq[Theme]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentThemes: Future[List[PersistentTheme]] = Future(NamedDB(Symbol("READ")).retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentTheme.as(themeAlias))
              .leftJoin(PersistentThemeTranslation.as(themeTranslationAlias))
              .on(themeAlias.uuid, themeTranslationAlias.themeUuid)
          }.one(PersistentTheme.apply())
            .toMany(PersistentThemeTranslation.opt(themeTranslationAlias))
            .map { (theme, translations) =>
              theme.copy(themeTranslations = translations.toVector)
            }
            .list
            .apply()
      })

      for {
        persistentThemes <- futurePersistentThemes
        tagsIds: Seq[TagId] = persistentThemes.flatMap(_.tagsIdsFromSlug)
        persistentTags <- persistentTagService.findAllFromIds(tagsIds)
      } yield persistentThemes.map(_.toTheme(persistentTags))
    }

    override def persist(theme: Theme): Future[Theme] = {
      implicit val context: EC = writeExecutionContext
      val tagsIds: String =
        theme.tags.map(_.tagId.value).mkString(DefaultPersistentThemeServiceComponent.TAG_SEPARATOR.toString)
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTheme)
            .namedValues(
              column.uuid -> theme.themeId.value,
              column.actionsCount -> theme.actionsCount,
              column.proposalsCount -> theme.proposalsCount,
              column.country -> theme.country.value,
              column.color -> theme.color,
              column.gradientFrom -> theme.gradient.map(_.from),
              column.gradientTo -> theme.gradient.map(_.to),
              column.tagsIds -> tagsIds,
              column.createdAt -> DateHelper.now(),
              column.updatedAt -> DateHelper.now()
            )
        }.execute().apply()
      }).flatMap(_ => Future.traverse(theme.translations)(translation => addTranslationToTheme(translation, theme)))
        .map(_ => theme)
    }

    override def addTranslationToTheme(translation: ThemeTranslation, theme: Theme): Future[Theme] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB(Symbol("WRITE")).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentThemeTranslation)
            .namedValues(
              themeTranslationcolumn.themeUuid -> theme.themeId.value,
              themeTranslationcolumn.slug -> translation.slug,
              themeTranslationcolumn.title -> translation.title,
              themeTranslationcolumn.language -> translation.language.value
            )
        }.execute().apply()
      }).map(_ => theme)
    }

  }
}

object DefaultPersistentThemeServiceComponent {

  val TAG_SEPARATOR = '|'

  case class PersistentThemeTranslation(themeUuid: String, slug: String, title: String, language: String) {
    def toThemeTranslation: ThemeTranslation =
      ThemeTranslation(slug = slug, title = title, language = Language(language))
  }

  case class PersistentTheme(uuid: String,
                             themeTranslations: Seq[PersistentThemeTranslation],
                             actionsCount: Int,
                             proposalsCount: Int,
                             country: String,
                             color: String,
                             gradientFrom: Option[String],
                             gradientTo: Option[String],
                             tagsIds: Option[String],
                             createdAt: ZonedDateTime,
                             updatedAt: ZonedDateTime) {

    def tagsIdsFromSlug: Seq[TagId] = tagsIds.getOrElse("").split(TAG_SEPARATOR).toIndexedSeq.map(toTagId)

    def toTheme(allTags: Seq[Tag]): Theme = {
      val tags: Seq[Tag] = tagsIdsFromSlug.flatMap(tagId => allTags.find(_.tagId == tagId))
      Theme(
        themeId = ThemeId(uuid),
        questionId = None,
        translations = themeTranslations.map(_.toThemeTranslation),
        actionsCount = actionsCount,
        proposalsCount = proposalsCount,
        votesCount = 0,
        country = Country(country),
        color = color,
        gradient = for {
          from <- gradientFrom
          to   <- gradientTo
        } yield GradientColor(from, to),
        tags = tags
      )
    }

    def toTagId(slug: String): TagId = TagId(slug)
  }

  object PersistentThemeTranslation
      extends SQLSyntaxSupport[PersistentThemeTranslation]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] = Seq("theme_uuid", "slug", "title", "language")

    override val tableName: String = "theme_translation"

    lazy val themeTranslationAlias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentThemeTranslation], PersistentThemeTranslation] =
      syntax("tt")

    def opt(
      themeTranslation: SyntaxProvider[PersistentThemeTranslation]
    )(resultSet: WrappedResultSet): Option[PersistentThemeTranslation] =
      resultSet
        .stringOpt(themeTranslation.resultName.themeUuid)
        .map(_ => PersistentThemeTranslation(themeTranslation.resultName)(resultSet))

    def apply(
      themeTranslationResultName: ResultName[PersistentThemeTranslation] = themeTranslationAlias.resultName
    )(resultSet: WrappedResultSet): PersistentThemeTranslation = {
      PersistentThemeTranslation.apply(
        themeUuid = resultSet.string(themeTranslationResultName.themeUuid),
        slug = resultSet.string(themeTranslationResultName.slug),
        title = resultSet.string(themeTranslationResultName.title),
        language = resultSet.string(themeTranslationResultName.language)
      )
    }
  }

  object PersistentTheme extends SQLSyntaxSupport[PersistentTheme] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] = Seq(
      "uuid",
      "actions_count",
      "proposals_count",
      "country",
      "color",
      "gradient_from",
      "gradient_to",
      "tags_ids",
      "created_at",
      "updated_at"
    )

    override val tableName: String = "theme"

    lazy val themeAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentTheme], PersistentTheme] = syntax("th")

    def apply(
      themeResultName: ResultName[PersistentTheme] = themeAlias.resultName
    )(resultSet: WrappedResultSet): PersistentTheme = {
      PersistentTheme.apply(
        uuid = resultSet.string(themeResultName.uuid),
        themeTranslations = Seq.empty,
        actionsCount = resultSet.int(themeResultName.actionsCount),
        proposalsCount = resultSet.int(themeResultName.proposalsCount),
        country = resultSet.string(themeResultName.country),
        color = resultSet.string(themeResultName.color),
        gradientFrom = resultSet.stringOpt(themeResultName.gradientFrom),
        gradientTo = resultSet.stringOpt(themeResultName.gradientTo),
        tagsIds = resultSet.stringOpt(themeResultName.tagsIds),
        createdAt = resultSet.zonedDateTime(themeResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(themeResultName.updatedAt)
      )
    }
  }

}
