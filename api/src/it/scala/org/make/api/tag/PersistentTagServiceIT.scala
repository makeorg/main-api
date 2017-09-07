package org.make.api.tag

import org.make.api.DatabaseTest
import org.make.core.reference.{Tag, TagId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class PersistentTagServiceIT extends DatabaseTest with DefaultPersistentTagServiceComponent {

  val stark: Tag = Tag("Stark")
  val targaryen: Tag = Tag("Targaryen")
  val lannister: Tag = Tag("Lannister")
  val bolton: Tag = Tag("Bolton")
  val greyjoy: Tag = Tag("Greyjoy")

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

        And("the tag id must be stark")
        tag.tagId.value shouldBe "stark"
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
        foundTags         <- persistentTagService.findAllEnabled()
      } yield foundTags -> persistedTagsList

      whenReady(futureTagsLists, Timeout(3.seconds)) {
        case (persisted, found) =>
          Then("result should contain a list of tags of targaryen, lannister, bolton and greyjoy.")
          found.forall(persisted.contains) should be(true)
      }
    }
  }

}
