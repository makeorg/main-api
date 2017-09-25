package org.make.api.theme

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.ShortenedNames
import org.make.api.theme.DefaultPersistentThemeServiceComponent.{PersistentTheme, PersistentThemeTranslation}
import org.make.core.DateHelper
import org.make.core.reference._
import scalikejdbc._
import org.make.api.technical.DatabaseTransactions._
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

  override lazy val persistentThemeService = new PersistentThemeService with ShortenedNames with StrictLogging {

    private val themeAlias = PersistentTheme.themeAlias
    private val themeTranslationAlias = PersistentThemeTranslation.themeTranslationAlias
    private val column = PersistentTheme.column
    private val themeTranslationcolumn = PersistentThemeTranslation.column

    override def findAll(): Future[Seq[Theme]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentThemes: Future[List[PersistentTheme]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            select
              .from(PersistentTheme.as(themeAlias))
              .leftJoin(PersistentThemeTranslation.as(themeTranslationAlias))
              .on(themeAlias.uuid, themeTranslationAlias.themeUuid)
          }.one(PersistentTheme.apply())
            .toMany(PersistentThemeTranslation.opt(themeTranslationAlias))
            .map { (theme, translations) =>
              theme.copy(themeTranslations = translations)
            }
            .list
            .apply()
      })

      for {
        persistentThemes <- futurePersistentThemes
        tagsIds: Seq[TagId] = persistentThemes.flatMap(_.tagsIdsFromSlug)
        persistentTags <- persistentTagService.findAllEnabledFromIds(tagsIds)
      } yield persistentThemes.map(_.toTheme(persistentTags))
    }

    override def persist(theme: Theme): Future[Theme] = {
      implicit val context: EC = writeExecutionContext
      val tagsIds: String = theme.tags.map(_.tagId.value).mkString(DefaultPersistentThemeServiceComponent.TAG_SEPARATOR)
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentTheme)
            .namedValues(
              column.uuid -> theme.themeId.value,
              column.actionsCount -> theme.actionsCount,
              column.proposalsCount -> theme.proposalsCount,
              column.country -> theme.country,
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
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentThemeTranslation)
            .namedValues(
              themeTranslationcolumn.themeUuid -> theme.themeId.value,
              themeTranslationcolumn.slug -> translation.slug,
              themeTranslationcolumn.title -> translation.title,
              themeTranslationcolumn.language -> translation.language
            )
        }.execute().apply()
      }).map(_ => theme)
    }

  }
}

object DefaultPersistentThemeServiceComponent {

  val TAG_SEPARATOR = ","

  case class PersistentThemeTranslation(themeUuid: String, slug: String, title: String, language: String)

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

    def tagsIdsFromSlug: Seq[TagId] = tagsIds.getOrElse("").split(TAG_SEPARATOR).map(toTagId)

    def toTheme(allTags: Seq[Tag]): Theme = {
      val tags: Seq[Tag] = tagsIdsFromSlug.flatMap(tagId => allTags.find(_.tagId == tagId))
      Theme(
        themeId = ThemeId(uuid),
        translations = themeTranslations.map(
          trans => ThemeTranslation(slug = trans.slug, title = trans.title, language = trans.language)
        ),
        actionsCount = actionsCount,
        proposalsCount = proposalsCount,
        country = country,
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
