package org.make.api.theme

import java.time.ZonedDateTime

import org.make.api.DatabaseTest
import org.make.core.tag.{Tag, TagId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class PersistentThemeServiceIT extends DatabaseTest with DefaultPersistentTagServiceComponent {

  val before: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")

  val targaryen: Tag = Tag("Targaryen")
  val stark: Tag = Tag("Stark")
  val lannister: Tag = Tag("Lannister")

  feature("A tag can be persisted") {
    scenario("Persist a tag and get the persisted tag") {
      Given(s"""a tag "${targaryen.label}"""")
      When(s"""I persist "${targaryen.label}"""")
      And("I get the persisted tag")
      val futureTag: Future[Option[Tag]] = persistentTagService
        .persist(targaryen)
        .flatMap(_ => persistentTagService.get(targaryen.tagId))(readExecutionContext)

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be an instance of Tag")
        val tag = result.get
        tag shouldBe a[Tag]

        And("the tag label must be Targaryen")
        tag.label shouldBe "Targaryen"

        And("the tag id must be targaryen")
        tag.tagId.value shouldBe "targaryen"
      }
    }
  }

  feature("One tag can be retrieve") {
    scenario("Get tag by tagId") {
      Given(s"""a persisted tag "${targaryen.label}"""")
      When(s"""I search the tag by tagId "${targaryen.tagId.value}"""")
      val futureTag: Future[Option[Tag]] = persistentTagService.get(targaryen.tagId)

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be an instance of Tag")
        val tag = result.get
        tag shouldBe a[Tag]

        And("the tag label must be Targaryen")
        tag.label shouldBe "Targaryen"

        And("the tag id must be targaryen")
        tag.tagId.value shouldBe "targaryen"
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
    scenario("Get a list of enabled tags") {
      Given(s"""a list of persisted tags:
               |label = ${targaryen.label}, tagId = ${targaryen.tagId.value}
               |label = ${stark.label}, tagId = ${stark.tagId.value}
               |label = ${lannister.label}, tagId = ${lannister.tagId.value}
        """.stripMargin)
      val futurePersistedTagList: Future[Seq[Tag]] = for {
        tagStark     <- persistentTagService.persist(stark)
        tagLannister <- persistentTagService.persist(lannister)
      } yield Seq(targaryen, tagStark, tagLannister)
      When("""I retrieve the tags list""")
      val futureTagsLists: Future[(Seq[Tag], Seq[Tag])] = for {
        persistedTagsList <- futurePersistedTagList
        foundTags         <- persistentTagService.findEnabled()
      } yield foundTags -> persistedTagsList

      whenReady(futureTagsLists, Timeout(3.seconds)) { persistedFound =>
        Then("result should be None")
        persistedFound._1.size shouldBe persistedFound._2.size
        persistedFound._1.toSet shouldBe persistedFound._2.toSet
      }
    }
  }

}
