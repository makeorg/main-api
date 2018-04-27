package org.make.api.tagtype

import org.make.api.DatabaseTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.tag._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentTagTypeServiceIT
    extends DatabaseTest
    with DefaultPersistentTagTypeServiceComponent
    with DefaultIdGeneratorComponent {

  def newTagType(label: String): TagType =
    TagType(tagTypeId = idGenerator.nextTagTypeId(), label = label, display = TagTypeDisplay.Displayed)

  val stark: TagType = newTagType("Stark")

  val targaryen: TagType = newTagType("Targaryen")
  val lannister: TagType = newTagType("Lannister")
  val bolton: TagType = newTagType("Bolton")
  val greyjoy: TagType = newTagType("Greyjoy")

  val tully: TagType = newTagType("Tully")
  val baratheon: TagType = newTagType("Baratheon")
  val martell: TagType = newTagType("Martell")
  val tyrell: TagType = newTagType("Tyrell")

  feature("One tagType can be persisted and retrieved") {
    scenario("Get tagType by tagTypeId") {
      Given(s"""a persisted tagType "${stark.label}"""")
      When(s"""I search the tagType by tagTypeId "${stark.tagTypeId.value}"""")
      val futureTagType: Future[Option[TagType]] = for {
        _            <- persistentTagTypeService.persist(stark)
        tagTypeStark <- persistentTagTypeService.get(stark.tagTypeId)
      } yield tagTypeStark

      whenReady(futureTagType, Timeout(3.seconds)) { result =>
        Then("the tagType label must be Stark")
        result.map(_.label) shouldBe Some("Stark")
      }
    }

    scenario("Get tagType by tagTypeId that does not exists") {
      Given("""a nonexistent tagType "fake"""")
      When("""I search the tagType from tagTypeId "fake"""")
      val futureTagTypeId: Future[Option[TagType]] = persistentTagTypeService.get(TagTypeId("fake"))

      whenReady(futureTagTypeId, Timeout(3.seconds)) { result =>
        Then("result should be None")
        result shouldBe None
      }
    }
  }

  feature("A list of tagTypes can be retrieved") {
    scenario("Get a list of all enabled tagTypes") {
      Given(s"""a list of persisted tagTypes:
               |label = ${targaryen.label}, tagTypeId = ${targaryen.tagTypeId.value}
               |label = ${lannister.label}, tagTypeId = ${lannister.tagTypeId.value}
               |label = ${bolton.label}, tagTypeId = ${bolton.tagTypeId.value}
               |label = ${greyjoy.label}, tagTypeId = ${greyjoy.tagTypeId.value}
        """.stripMargin)
      val futurePersistedTagTypeList: Future[Seq[TagType]] = for {
        tagTypeTargaryen <- persistentTagTypeService.persist(targaryen)
        tagTypeLannister <- persistentTagTypeService.persist(lannister)
        tagTypeBolton    <- persistentTagTypeService.persist(bolton)
        tagTypeGreyjoy   <- persistentTagTypeService.persist(greyjoy)
      } yield Seq(tagTypeLannister, tagTypeBolton, tagTypeGreyjoy)

      When("""I retrieve the tagTypes list""")
      val futureTagTypesLists: Future[(Seq[TagType], Seq[TagType])] = for {
        persistedTagTypesList <- futurePersistedTagTypeList
        foundTagTypes         <- persistentTagTypeService.findAll()
      } yield foundTagTypes -> persistedTagTypesList

      whenReady(futureTagTypesLists, Timeout(3.seconds)) {
        case (persisted, found) =>
          Then("result should contain a list of tagTypes of targaryen, lannister, bolton and greyjoy.")
          found.forall(persisted.contains) should be(true)
      }
    }
  }

}