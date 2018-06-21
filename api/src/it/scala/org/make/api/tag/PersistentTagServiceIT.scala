package org.make.api.tag

import org.make.api.DatabaseTest
import org.make.api.operation.DefaultPersistentOperationServiceComponent
import org.make.api.tagtype.DefaultPersistentTagTypeServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.theme.DefaultPersistentThemeServiceComponent
import org.make.core.operation._
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.sequence.SequenceId
import org.make.core.tag._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentTagServiceIT
    extends DatabaseTest
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentThemeServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40003

  def newTag(label: String, operationId: Option[OperationId] = None, themeId: Option[ThemeId] = None): Tag = Tag(
    tagId = idGenerator.nextTagId(),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagType.LEGACY.tagTypeId,
    operationId = operationId,
    themeId = themeId,
    country = "FR",
    language = "fr"
  )

  val stark: Tag = newTag("Stark")

  val targaryen: Tag = newTag("Targaryen")
  val lannister: Tag = newTag("Lannister")
  val bolton: Tag = newTag("Bolton")
  val greyjoy: Tag = newTag("Greyjoy")

  val tully: Tag = newTag("Tully")
  val baratheon: Tag = newTag("Baratheon")
  val martell: Tag = newTag("Martell")
  val tyrell: Tag = newTag("Tyrell")

  private val developementDurableTheme = ThemeId("036f24fa-dc32-4808-bca9-7ccec1665585")
  val bron: Tag = newTag("Bron")
  val weirdTag: Tag = newTag("weird%Tag")
  val snow: Tag = newTag("Snow", operationId = Some(OperationId("vff")))
  val lancaster: Tag = newTag("Lancaster", themeId = Some(developementDurableTheme))
  val byron: Tag = newTag("Byron")

  val fakeOperation = Operation(
    OperationStatus.Active,
    OperationId("fakeOperation"),
    "fake-operation",
    Seq.empty,
    "fr",
    List.empty,
    None,
    None,
    Seq.empty
  )

  feature("One tag can be persisted and retrieved") {
    scenario("Get tag by tagId") {
      Given(s"""a persisted tag "${stark.label}"""")
      When(s"""I search the tag by tagId "${stark.tagId.value}"""")
      val futureTag: Future[Option[Tag]] = for {
        _        <- persistentTagService.persist(stark)
        tagStark <- persistentTagService.get(stark.tagId)
      } yield tagStark

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be an instance of Tag")
        val tag = result.get
        tag shouldBe a[Tag]

        And("the tag label must be Stark")
        tag.label shouldBe "Stark"
      }
    }

    scenario("Get tag by tagId that does not exists") {
      Given("""a nonexistent tag "fake"""")
      When("""I search the tag from tagId "fake"""")
      val futureTagId: Future[Option[Tag]] = persistentTagService.get(TagId("fake"))

      whenReady(futureTagId, Timeout(3.seconds)) { result =>
        Then("result should be None")
        result shouldBe None
      }
    }
  }

  feature("A list of tags can be retrieved") {
    scenario("Get a list of all tags") {
      Given(s"""a list of persisted tags:
               |label = ${targaryen.label}, tagId = ${targaryen.tagId.value}
               |label = ${lannister.label}, tagId = ${lannister.tagId.value}
               |label = ${bolton.label}, tagId = ${bolton.tagId.value}
               |label = ${greyjoy.label}, tagId = ${greyjoy.tagId.value}
        """.stripMargin)
      val futurePersistedTagList: Future[Seq[Tag]] = for {
        tagTargaryen <- persistentTagService.persist(targaryen)
        tagLannister <- persistentTagService.persist(lannister)
        tagBolton    <- persistentTagService.persist(bolton)
        tagGreyjoy   <- persistentTagService.persist(greyjoy)
      } yield Seq(tagLannister, tagBolton, tagGreyjoy)

      When("""I retrieve the tags list""")
      val futureTagsLists: Future[(Seq[Tag], Seq[Tag])] = for {
        persistedTagsList <- futurePersistedTagList
        foundTags         <- persistentTagService.findAll()
      } yield foundTags -> persistedTagsList

      whenReady(futureTagsLists, Timeout(3.seconds)) {
        case (persisted, found) =>
          Then("result should contain a list of tags of targaryen, lannister, bolton and greyjoy.")
          found.forall(persisted.contains) should be(true)
      }
    }
  }

  feature("Find a tag") {
    scenario("find from normal label") {
      Given(s"""a persisted tag "${bron.label}"""")
      When(s"""I search the tag by a part of its label: "${bron.label.take(3)}"""")
      val futureTag: Future[Seq[Tag]] = for {
        _       <- persistentTagService.persist(bron)
        tagBron <- persistentTagService.findByLabelLike(bron.label.take(3))
      } yield tagBron

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be a list af at least one tag")
        result.length should be >= 1

        And(s"the tag ${bron.label} should be in the list")
        result.contains(bron) shouldBe true
      }
    }

    scenario("find from label with the % character") {
      Given(s"""a persisted tag "${weirdTag.label}"""")
      When(s"""I search the tag by a part of its label: "${weirdTag.label.take(3)}"""")
      val futureTag: Future[Seq[Tag]] = for {
        _           <- persistentTagService.persist(weirdTag)
        tagWeirdTag <- persistentTagService.findByLabelLike(weirdTag.label.take(3))
      } yield tagWeirdTag

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be a list af at least one tag")
        result.length should be >= 1

        And(s"the tag ${weirdTag.label} should be in the list")
        result.contains(weirdTag) shouldBe true
      }
    }

//    test ignored because the persist operation does not work.
    ignore("find from operationId") {
      Given(s"""a persisted tag "${snow.label}" and a persisted operation "${fakeOperation.slug}"""")
      When("""I search the tag by its operation""")
      val futureTag: Future[Seq[Tag]] = for {
        _                <- persistentOperationService.persist(fakeOperation)
        _                <- persistentTagService.persist(snow)
        tagFakeOperation <- persistentTagService.findByOperationId(fakeOperation.operationId)
      } yield tagFakeOperation

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be a list af at least one tag")
        result.length should be >= 1

        And(s"the tag ${snow.label} should be in the list")
        result.contains(snow) shouldBe true
      }
    }

    scenario("find from themeId") {
      Given(s"""a persisted tag "${lancaster.label}"""")
      When("""I search the tag by its theme""")
      val futureTag: Future[Seq[Tag]] = for {
        _            <- persistentTagService.persist(lancaster)
        tagLancaster <- persistentTagService.findByThemeId(lancaster.themeId.get)
      } yield tagLancaster

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be a list af at least one tag")
        result.length should be >= 1

        And(s"the tag ${lancaster.label} should be in the list")
        result.contains(lancaster) shouldBe true
      }
    }
  }

  feature("update a tag") {
    scenario("update an existing tag") {
      Given(s"""a persisted tag "${byron.label}" """)
      When("""I update this tag label to "not a got character"""")
      val futureTag: Future[Option[Tag]] = for {
        _        <- persistentTagService.persist(byron)
        tagByron <- persistentTagService.update(byron.copy(label = "not a got character"))
      } yield tagByron

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then(s"""result should have the same id as "${byron.label}" tag""")
        result.map(_.tagId) should be(Some(byron.tagId))
        And("the tag label should have changed")
        result.map(_.label) should be(Some("not a got character"))
      }
    }

    scenario("update a non existing tag") {
      Given("a non existing tag")
      When("""I update this tag label to "not here"""")
      val futureTag: Future[Option[Tag]] = persistentTagService.update(newTag("not here"))

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be empty")
        result should be(None)
      }
    }
  }

  feature("Search tags") {
    scenario("Search tags by label") {
      val tagTypeFirst: TagType =
        TagType(tagTypeId = TagTypeId("first"), label = "First", display = TagTypeDisplay.Displayed)
      val tagTypeSecond: TagType =
        TagType(tagTypeId = TagTypeId("second"), label = "Second", display = TagTypeDisplay.Displayed)
      val operationIdFirst: OperationId = OperationId("opefirst")
      val operationIdSecond: OperationId = OperationId("opesecond")
      val themeIdFirst: ThemeId = ThemeId("themefirst")
      val themeIdSecond: ThemeId = ThemeId("themesecond")

      val hestia: Tag =
        targaryen.copy(
          tagId = TagId("hestia"),
          tagTypeId = tagTypeFirst.tagTypeId,
          label = "hestialabel",
          operationId = Some(operationIdFirst),
          country = "FR",
          language = "fr"
        )
      val athena: Tag =
        targaryen.copy(
          tagId = TagId("athena"),
          tagTypeId = tagTypeFirst.tagTypeId,
          label = "athenalabel",
          operationId = Some(operationIdFirst),
          country = "FR",
          language = "br"
        )
      val ariane: Tag =
        targaryen.copy(
          tagId = TagId("ariane"),
          tagTypeId = tagTypeFirst.tagTypeId,
          label = "arianelabel",
          operationId = Some(operationIdSecond),
          country = "FR",
          language = "fr"
        )
      val hera: Tag = targaryen.copy(
        tagId = TagId("hera"),
        tagTypeId = tagTypeSecond.tagTypeId,
        label = "heralabel",
        operationId = Some(operationIdSecond),
        country = "BR",
        language = "br"
      )
      val clio: Tag = targaryen.copy(
        tagId = TagId("clio"),
        tagTypeId = tagTypeSecond.tagTypeId,
        label = "cliolabel",
        themeId = Some(themeIdFirst),
        country = "BR",
        language = "fr"
      )
      val thalia: Tag =
        targaryen.copy(
          tagId = TagId("thalia"),
          tagTypeId = tagTypeSecond.tagTypeId,
          label = "thalialabel",
          themeId = Some(themeIdFirst),
          country = "BR",
          language = "br"
        )
      val calliope: Tag =
        targaryen.copy(
          tagId = TagId("calliope"),
          tagTypeId = tagTypeSecond.tagTypeId,
          label = "calliopelabel",
          themeId = Some(themeIdSecond)
        )

      val baseTheme: Theme = Theme(
        themeId = themeIdFirst,
        translations = Seq.empty,
        actionsCount = 0,
        proposalsCount = 0,
        votesCount = 0,
        country = "FR",
        color = "",
        gradient = None,
        tags = Seq.empty
      )
      val persistedTags: Future[Seq[Tag]] = for {
        _ <- persistentThemeService.persist(baseTheme.copy(themeId = themeIdFirst))
        _ <- persistentThemeService.persist(baseTheme.copy(themeId = themeIdSecond))
        _ <- persistentOperationService.persist(
          fakeOperation.copy(
            operationId = operationIdFirst,
            slug = "ope-first",
            countriesConfiguration = Seq(
              OperationCountryConfiguration(
                countryCode = "FR",
                tagIds = Seq.empty,
                landingSequenceId = SequenceId("fr"),
                startDate = None,
                endDate = None
              )
            )
          )
        )
        _ <- persistentOperationService.persist(
          fakeOperation.copy(
            operationId = operationIdSecond,
            slug = "ope-second",
            countriesConfiguration = Seq(
              OperationCountryConfiguration(
                countryCode = "FR",
                tagIds = Seq.empty,
                landingSequenceId = SequenceId("fr"),
                startDate = None,
                endDate = None
              )
            )
          )
        )
        _        <- persistentTagTypeService.persist(tagTypeFirst)
        _        <- persistentTagTypeService.persist(tagTypeSecond)
        hestia   <- persistentTagService.persist(hestia)
        athena   <- persistentTagService.persist(athena)
        ariane   <- persistentTagService.persist(ariane)
        hera     <- persistentTagService.persist(hera)
        clio     <- persistentTagService.persist(clio)
        thalia   <- persistentTagService.persist(thalia)
        calliope <- persistentTagService.persist(calliope)
      } yield Seq(hestia, athena, ariane, hera, clio, thalia, calliope)

      val tagList: Seq[String] = Seq(hestia, athena, ariane, hera, clio, thalia, calliope).map { tag =>
        s"label = ${tag.label}, tagId = ${tag.tagId.value}, operationId = ${tag.operationId.map(_.value).getOrElse("None")}"
      }

      Given(s"a list of persisted tags: \n ${tagList.mkString("\n")}")

      When("I search tags by label 'calliope'")
      val futureTagListResult: Future[Seq[Tag]] = for {
        _ <- persistedTags
        results <- persistentTagService.search(
          0,
          Some(10),
          None,
          None,
          PersistentTagFilter.empty.copy(label = Some("calliopelabel"))
        )
      } yield results

      whenReady(futureTagListResult, Timeout(3.seconds)) { tags =>
        Then("I get a list with one result")
        tags.length should be(1)
        tags.count(_.label == calliope.label) should be(tags.length)
      }

      When("I search tags by operation Id 'opefirst'")
      val futureTagListResultOperation: Future[Seq[Tag]] = persistentTagService.search(
        0,
        Some(10),
        None,
        None,
        PersistentTagFilter.empty.copy(operationId = Some(operationIdFirst))
      )

      whenReady(futureTagListResultOperation, Timeout(3.seconds)) { tags =>
        Then("I get a list with two results")
        tags.length should be(2)
        tags.count(_.operationId.contains(operationIdFirst)) should be(tags.length)
      }

      When("I search tags by operation Id 'opesecond'")
      val futureTagListResultOperationSecond: Future[Seq[Tag]] = persistentTagService.search(
        0,
        Some(10),
        None,
        None,
        PersistentTagFilter.empty.copy(operationId = Some(operationIdSecond))
      )

      whenReady(futureTagListResultOperationSecond, Timeout(3.seconds)) { tags =>
        Then("I get a list with two results")
        tags.length should be(2)
        tags.count(_.operationId.contains(operationIdSecond)) should be(tags.length)
      }

      When(s"I search tags by tag type '${tagTypeFirst.label}'")
      val futureTagListResultTagTypeFirst: Future[Seq[Tag]] = persistentTagService.search(
        0,
        Some(10),
        None,
        None,
        PersistentTagFilter.empty.copy(tagTypeId = Some(tagTypeFirst.tagTypeId))
      )

      whenReady(futureTagListResultTagTypeFirst, Timeout(3.seconds)) { tags =>
        Then("I get a list with three results")
        tags.length should be(3)
        tags.count(_.tagTypeId == tagTypeFirst.tagTypeId) should be(tags.length)
      }

      When(s"I search tags by theme '${themeIdFirst.value}'")
      val futureTagListResultThemeFirst: Future[Seq[Tag]] =
        persistentTagService.search(
          0,
          Some(10),
          None,
          None,
          PersistentTagFilter.empty.copy(themeId = Some(themeIdFirst))
        )

      whenReady(futureTagListResultThemeFirst, Timeout(3.seconds)) { tags =>
        Then("I get a list with two results")
        tags.length should be(2)
        tags.count(_.themeId.contains(themeIdFirst)) should be(tags.length)
      }

      When("I search tags by country 'BR'")
      val futureTagListResultBr: Future[Seq[Tag]] =
        persistentTagService.search(0, Some(10), None, None, PersistentTagFilter.empty.copy(country = Some("BR")))

      whenReady(futureTagListResultBr, Timeout(3.seconds)) { tags =>
        Then("I get a list with three results")
        tags.length should be(3)
        tags.count(_.country == "BR") should be(tags.length)
      }

      When("I search tags by language 'br'")
      val futureTagListResultLanguageBr: Future[Seq[Tag]] =
        persistentTagService.search(0, Some(10), None, None, PersistentTagFilter.empty.copy(language = Some("br")))

      whenReady(futureTagListResultLanguageBr, Timeout(3.seconds)) { tags =>
        Then("I get a list with three results")
        tags.length should be(3)
        tags.count(_.language == "br") should be(tags.length)
      }

      When(s"""I search tags by 
           |tagTypeId = '${hera.tagTypeId.value}',
           |label = '${hera.label}', 
           |operation = ${operationIdSecond.value}, 
           |country = 'BR' and 
           |language = 'br' """.stripMargin)
      val futureTagListResultHera: Future[Seq[Tag]] =
        persistentTagService.search(
          0,
          Some(10),
          None,
          None,
          PersistentTagFilter.empty.copy(
            tagTypeId = Some(hera.tagTypeId),
            label = Some(hera.label),
            operationId = hera.operationId,
            country = Some(hera.country),
            language = Some(hera.language)
          )
        )

      whenReady(futureTagListResultHera, Timeout(3.seconds)) { tags =>
        Then("I get a list with one result")
        tags.length should be(1)
        tags.last should be(hera)
      }

    }
  }
}
