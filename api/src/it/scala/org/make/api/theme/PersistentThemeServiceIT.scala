package org.make.api.theme

import java.sql.SQLException

import org.make.api.DatabaseTest
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.core.SlugHelper
import org.make.core.reference._
import org.make.core.tag.Tag
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class PersistentThemeServiceIT
    extends DatabaseTest
    with DefaultPersistentThemeServiceComponent
    with DefaultPersistentTagServiceComponent {

  val stark: Tag = Tag("Stark")
  val targaryen: Tag = Tag("Targaryen")
  val lannister: Tag = Tag("Lannister")
  val whiteWalker: Tag = Tag("White walker")

  val winterIsComingTags: Seq[Tag] = Seq(stark, targaryen, lannister)
  val winterIsComing: Theme = Theme(
    themeId = ThemeId("winterIsComingId"),
    translations =
      Seq(ThemeTranslation(slug = SlugHelper("winter-is-coming"), title = "Winter is coming", language = "dk")),
    actionsCount = 7,
    proposalsCount = 42,
    votesCount = 0,
    country = "WE",
    color = "#00FFFF",
    gradient = Some(GradientColor("#0FF", "#0F0")),
    tags = winterIsComingTags
  )

  val winterIsHereTags: Seq[Tag] = Seq(whiteWalker)
  val winterIsHere: Theme = Theme(
    themeId = ThemeId("winterIsHere"),
    translations = Seq(ThemeTranslation(slug = SlugHelper("winter-is-here"), title = "Winter is here", language = "dk")),
    actionsCount = 0,
    proposalsCount = 1000,
    votesCount = 0,
    country = "WE",
    color = "#FFFFdd",
    gradient = Some(GradientColor("#FFC", "#FFF")),
    tags = winterIsHereTags
  )

  val nonExistantTheme = Theme(
    themeId = ThemeId("void"),
    translations = Seq(ThemeTranslation(slug = SlugHelper("nowhere"), title = "Nowhere", language = "xx")),
    actionsCount = 0,
    proposalsCount = 0,
    votesCount = 0,
    country = "XX",
    color = "#000000",
    gradient = Some(GradientColor("#000", "#000")),
    tags = Seq.empty
  )

  val frozen: Theme = Theme(
    themeId = ThemeId("frozen"),
    translations = Seq(
      ThemeTranslation(slug = SlugHelper("let-it-gooo"), title = "Let it gooo", language = "en"),
      ThemeTranslation(slug = SlugHelper("libereeeee"), title = "Libéréééée", language = "fr")
    ),
    actionsCount = 7,
    proposalsCount = 42,
    votesCount = 0,
    country = "WE",
    color = "#00FFFF",
    gradient = Some(GradientColor("#0FF", "#0F0")),
    tags = Seq.empty
  )
  val mandarinTranslation = ThemeTranslation(slug = SlugHelper("sui-ta-baaa"), title = "Sui ta baaa", language = "cmn")

  feature("A theme can be persisted") {
    scenario("Persist a theme and get the persisted theme in the list of theme with related tags") {
      Given(s"""a theme "${winterIsComing.translations.head.title}"""")
      When(s"""I persist "${winterIsComing.translations.head.title}"""")
      And(s"""I persist its tags "${stark.label}", "${targaryen.label}" and "${lannister.label}"""")
      And("I get the persisted theme")
      val futureTheme: Future[Seq[Theme]] = for {
        _ <- persistentTagService.persist(stark)
        _ <- persistentTagService.persist(targaryen)
        _ <- persistentTagService.persist(lannister)
        themeWinter <- persistentThemeService
          .persist(winterIsComing)
          .flatMap(_ => persistentThemeService.findAll())(readExecutionContext)
      } yield themeWinter

      whenReady(futureTheme, Timeout(3.seconds)) { themes =>
        Then("themes should be an instance of Seq[Theme]")
        themes shouldBe a[Seq[_]]
        themes.size should be > 0
        themes.head shouldBe a[Theme]

        And(s"the themes must contain ${winterIsComing.translations.head.title}")
        themes.exists(_.themeId.value == winterIsComing.themeId.value) should be(true)
        val themeWinterIsComing: Theme = themes.find(_.themeId.value == winterIsComing.themeId.value).get

        And(s"""the tags must be the list of its tags ${stark.label}, ${targaryen.label} and ${lannister.label}""")
        themeWinterIsComing.tags.size should be(winterIsComingTags.size)
        themeWinterIsComing.tags.forall(winterIsComingTags.contains) should be(true)
        winterIsComingTags.forall(themeWinterIsComing.tags.contains) should be(true)
      }
    }

    scenario("Persist a theme with non-existent tags and retrieve theme with empty list of tags") {
      Given(s"""a theme "${winterIsHere.translations.head.title}"""")
      When(s"""I persist "${winterIsHere.translations.head.title}"""")
      And("I get the persisted themes")
      val futureThemes: Future[Seq[Theme]] = persistentThemeService
        .persist(winterIsHere)
        .flatMap(_ => persistentThemeService.findAll())(readExecutionContext)

      whenReady(futureThemes, Timeout(3.seconds)) { themes =>
        Then("themes should be an instance of Seq[Theme]")
        themes shouldBe a[Seq[_]]
        themes.size should be > 0
        themes.head shouldBe a[Theme]

        And(s"the themes must contain ${winterIsHere.translations.head.title}")
        themes.exists(_.themeId.value == winterIsHere.themeId.value) should be(true)
        val themeWinterIsHere: Theme = themes.find(_.themeId.value == winterIsHere.themeId.value).get

        And("""the tags must be an empty list""")
        themeWinterIsHere.tags.size should be(0)
      }
    }

    scenario("Persist translations to a non-existent theme should fail") {
      Given(s"""a theme "${nonExistantTheme.translations.head.title}"""")
      When("I try to add translation")
      def futureFailedAddTranslation: Future[Theme] =
        persistentThemeService.addTranslationToTheme(nonExistantTheme.translations.head, nonExistantTheme)

      Then("I get a SQLException")
      intercept[SQLException] {
        logger.info("Expected exception: testing to add translation to non existent theme")
        Await.result(futureFailedAddTranslation, 5.seconds)
      }
    }

    scenario("Persist theme along with its translations and add some translations") {
      Given(s"""a theme "${frozen.translations.head.title}" with a translation "${frozen.translations.last.title}"""")
      When("""I persist this theme""")
      And("I get the persisted themes")
      val futureListThemesWithTranslations = persistentThemeService.persist(frozen).flatMap { _ =>
        persistentThemeService.findAll()
      }

      whenReady(futureListThemesWithTranslations, Timeout(3.seconds)) { themes =>
        Then("themes should be an instance of Seq[Theme]")
        themes shouldBe a[Seq[_]]
        themes.size should be > 0
        themes.head shouldBe a[Theme]

        And(s"""the themes must contain ${frozen.translations.head.title}""")
        themes.exists(_.themeId.value == frozen.themeId.value) should be(true)
        val foundFrozen: Theme = themes.find(_.themeId.value == frozen.themeId.value).get

        And("""the theme must contain two translations""")
        foundFrozen.translations.size should be(2)
        foundFrozen.translations.exists(_.language == frozen.translations.head.language) should be(true)
        foundFrozen.translations.exists(_.language == frozen.translations.last.language) should be(true)
      }

      Given(s"""the same persisted theme "${frozen.translations.head.title}"""")
      And(s"""I persist the translation to this theme:"${mandarinTranslation.title}"""")
      And("I get the persisted themes")
      val futureListWithSuiTaBa: Future[Seq[Theme]] =
        persistentThemeService.addTranslationToTheme(mandarinTranslation, frozen).flatMap { _ =>
          persistentThemeService.findAll()
        }

      Then(s"I get a list of themes containing ${frozen.translations.head.title}")
      whenReady(futureListWithSuiTaBa, Timeout(3.seconds)) { themes =>
        themes.exists(_.themeId.value == frozen.themeId.value) should be(true)
        val foundSuitaBa: Theme = themes.find(_.themeId.value == frozen.themeId.value).get

        And("""I can find the previous translation""")
        foundSuitaBa.translations.exists(_.language == frozen.translations.head.language) should be(true)
        foundSuitaBa.translations.exists(_.language == frozen.translations.last.language) should be(true)

        And(s"""I can find the new translation in ${mandarinTranslation.language}""")
        foundSuitaBa.translations.exists(_.language == mandarinTranslation.language) should be(true)
      }

    }
  }

}
