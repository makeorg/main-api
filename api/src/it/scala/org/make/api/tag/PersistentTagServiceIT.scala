package org.make.api.tag

import org.make.api.DatabaseTest
import org.make.api.operation.DefaultPersistentOperationServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.operation.{Operation, OperationId, OperationStatus}
import org.make.core.reference.ThemeId
import org.make.core.tag._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentTagServiceIT
    extends DatabaseTest
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultIdGeneratorComponent {

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
        _        <- persistentTagService.persist(bron)
        tagStark <- persistentTagService.findByLabelLike(bron.label.take(3))
      } yield tagStark

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
        _        <- persistentTagService.persist(weirdTag)
        tagStark <- persistentTagService.findByLabelLike(weirdTag.label.take(3))
      } yield tagStark

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
        _        <- persistentOperationService.persist(fakeOperation)
        _        <- persistentTagService.persist(snow)
        tagStark <- persistentTagService.findByOperationId(fakeOperation.operationId)
      } yield tagStark

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
        _        <- persistentTagService.persist(lancaster)
        tagStark <- persistentTagService.findByThemeId(lancaster.themeId.get)
      } yield tagStark

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be a list af at least one tag")
        result.length should be >= 1

        And(s"the tag ${lancaster.label} should be in the list")
        result.contains(lancaster) shouldBe true
      }
    }

  }

}
