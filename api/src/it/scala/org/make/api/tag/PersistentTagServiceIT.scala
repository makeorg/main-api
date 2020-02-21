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

package org.make.api.tag

import org.make.api.DatabaseTest
import org.make.api.operation.DefaultPersistentOperationServiceComponent
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tagtype.DefaultPersistentTagTypeServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentTagServiceIT
    extends DatabaseTest
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40003

  val fakeOperation = SimpleOperation(
    status = OperationStatus.Active,
    operationId = OperationId("fakeOperation"),
    slug = "fake-operation",
    defaultLanguage = Language("fr"),
    allowedSources = Seq.empty,
    operationKind = OperationKind.PublicConsultation,
    createdAt = None,
    updatedAt = None
  )

  def questionForOperation(operationId: OperationId,
                           country: Country = Country("FR"),
                           language: Language = Language("fr")): Question = {
    Question(
      questionId = QuestionId(operationId.value),
      slug = s"some-question-on-operation-${operationId.value}",
      operationId = Some(operationId),
      country = country,
      language = language,
      question = operationId.value
    )
  }

  def questionForTheme(themeId: ThemeId,
                       country: Country = Country("FR"),
                       language: Language = Language("fr")): Question = {
    Question(
      questionId = QuestionId(themeId.value),
      slug = s"some-question-on-theme-${themeId.value}",
      operationId = None,
      country = country,
      language = language,
      question = themeId.value
    )
  }

  val fakeQuestion: Question = questionForOperation(fakeOperation.operationId)

  def newTag(label: String, questionId: QuestionId, operationId: Option[OperationId] = None): Tag = Tag(
    tagId = idGenerator.nextTagId(),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagType.LEGACY.tagTypeId,
    operationId = operationId,
    country = Country("FR"),
    language = Language("fr"),
    questionId = Some(questionId)
  )

  val stark: Tag = newTag("Stark", QuestionId("stark"))

  val targaryen: Tag = newTag("Targaryen", QuestionId("Targaryen"))
  val lannister: Tag = newTag("Lannister", QuestionId("Lannister"))
  val bolton: Tag = newTag("Bolton", QuestionId("Bolton"))
  val greyjoy: Tag = newTag("Greyjoy", QuestionId("Greyjoy"))

  val tully: Tag = newTag("Tully", QuestionId("Tully"))
  val baratheon: Tag = newTag("Baratheon", QuestionId("Baratheon"))
  val martell: Tag = newTag("Martell", QuestionId("Martell"))
  val tyrell: Tag = newTag("Tyrell", QuestionId("Tyrell"))

  private val developementDurableQuestion = QuestionId("question-for-developement-durable-theme")
  val bron: Tag = newTag("Bron", QuestionId("Bron"))
  val weirdTag: Tag = newTag("weird%Tag", QuestionId("weird-Tag"))
  val snow: Tag = newTag("Snow", operationId = Some(fakeOperation.operationId), questionId = fakeQuestion.questionId)
  val lancaster: Tag =
    newTag("Lancaster", questionId = developementDurableQuestion)
  val byron: Tag = newTag("Byron", QuestionId("Byron"))

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
        _            <- persistentTagService.persist(targaryen)
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

    scenario("find a tag with exact label") {
      Given(s"""a persisted tag "${baratheon.label}"""")
      When(s"""I search the tag by its label: "${baratheon.label}"""")
      val futureTag1: Future[Seq[Tag]] = for {
        _            <- persistentTagService.persist(baratheon)
        tagBaratheon <- persistentTagService.findByLabel(baratheon.label)
      } yield tagBaratheon

      whenReady(futureTag1, Timeout(3.seconds)) { result =>
        Then("result should be a list af at least one tag")
        result.length should be >= 1

        And(s"the tag ${baratheon.label} should be in the list")
        result.contains(baratheon) shouldBe true
      }

      Given(s"""a persisted tag "${tully.label}"""")
      When(s"""I search the tag by a part of its label: "${tully.label.take(3)}"""")
      val futureTag2: Future[Seq[Tag]] = for {
        _        <- persistentTagService.persist(tully)
        tagTully <- persistentTagService.findByLabel(tully.label.take(3))
      } yield tagTully

      whenReady(futureTag2, Timeout(3.seconds)) { result =>
        And(s"the tag ${tully.label} should not be in the list")
        result.contains(tully) shouldBe false
      }
    }

    scenario("find from operationId") {
      Given(s"""a persisted tag "${snow.label}" and a persisted operation "${fakeOperation.slug}"""")
      When("""I search the tag by its operation""")
      val futureTag: Future[Seq[Tag]] = for {
        _                <- persistentOperationService.persist(fakeOperation)
        _                <- persistentQuestionService.persist(fakeQuestion)
        _                <- persistentTagService.persist(snow)
        tagFakeOperation <- persistentTagService.findByQuestion(fakeQuestion.questionId)
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
        tagLancaster <- persistentTagService.findByQuestion(lancaster.questionId.get)
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
      val futureTag: Future[Option[Tag]] = persistentTagService.update(newTag("not here", QuestionId("not-here")))

      whenReady(futureTag, Timeout(3.seconds)) { result =>
        Then("result should be empty")
        result should be(None)
      }
    }
  }

  feature("Search tags") {
    val tagTypeFirst: TagType =
      TagType(tagTypeId = TagTypeId("first"), label = "First", display = TagTypeDisplay.Displayed)
    val tagTypeSecond: TagType =
      TagType(tagTypeId = TagTypeId("second"), label = "Second", display = TagTypeDisplay.Hidden)
    val tagTypeThird: TagType =
      TagType(tagTypeId = TagTypeId("third"), label = "Third", display = TagTypeDisplay.Hidden)
    val tagTypeFourth: TagType =
      TagType(tagTypeId = TagTypeId("fourth"), label = "Fourth", display = TagTypeDisplay.Hidden)
    val tagTypeSeventh: TagType =
      TagType(tagTypeId = TagTypeId("seventh"), label = "Seventh", display = TagTypeDisplay.Hidden)
    val tagTypeHeighth: TagType =
      TagType(tagTypeId = TagTypeId("heighth"), label = "Heighth", display = TagTypeDisplay.Displayed)
    val operationIdFirst: OperationId = OperationId("opefirst")
    val questionFirst = questionForOperation(operationIdFirst)
    val operationIdSecond: OperationId = OperationId("opesecond")
    val questionSecond = questionForOperation(operationIdSecond)
    val operationIdThird: OperationId = OperationId("opethird")
    val questionThird = questionForOperation(operationIdThird)

    val themeIdFirst: ThemeId = ThemeId("themefirst")
    val questionThemeFirst = questionForTheme(themeIdFirst)

    val hestia: Tag =
      targaryen.copy(
        tagId = TagId("hestia"),
        tagTypeId = tagTypeFirst.tagTypeId,
        label = "hestialabel",
        operationId = Some(operationIdFirst),
        questionId = Some(questionFirst.questionId),
        country = Country("FR"),
        language = Language("fr")
      )
    val athena: Tag =
      targaryen.copy(
        tagId = TagId("athena"),
        tagTypeId = tagTypeFirst.tagTypeId,
        label = "athenalabel",
        operationId = Some(operationIdFirst),
        questionId = Some(questionFirst.questionId),
        country = Country("FR"),
        language = Language("br")
      )
    val ariane: Tag =
      targaryen.copy(
        tagId = TagId("ariane"),
        tagTypeId = tagTypeFirst.tagTypeId,
        label = "arianelabel",
        operationId = Some(operationIdSecond),
        questionId = Some(questionSecond.questionId),
        country = Country("FR"),
        language = Language("fr")
      )
    val hera: Tag = targaryen.copy(
      tagId = TagId("hera"),
      tagTypeId = tagTypeSeventh.tagTypeId,
      label = "heralabel",
      operationId = Some(operationIdThird),
      questionId = Some(questionThird.questionId),
      country = Country("BR"),
      language = Language("br")
    )
    val clio: Tag = targaryen.copy(
      tagId = TagId("clio"),
      tagTypeId = tagTypeThird.tagTypeId,
      label = "cliolabel",
      country = Country("BR"),
      language = Language("fr")
    )
    val thalia: Tag =
      targaryen.copy(
        tagId = TagId("thalia"),
        tagTypeId = tagTypeFourth.tagTypeId,
        label = "thalialabel",
        questionId = Some(questionThemeFirst.questionId),
        country = Country("BR"),
        language = Language("br")
      )
    val calliope: Tag =
      targaryen.copy(tagId = TagId("calliope"), tagTypeId = tagTypeHeighth.tagTypeId, label = "calliopelabel")

    scenario("Search tags by label") {

      Given(s"a persisted tag: '${calliope.label}'")

      When("I search tags by label 'calliope'")

      val futureTagListResultByLabel: Future[Seq[Tag]] = for {
        _ <- persistentTagTypeService.persist(tagTypeHeighth)
        _ <- persistentTagService.persist(calliope)
        result <- persistentTagService.find(
          start = 0,
          end = Some(10),
          sort = None,
          order = None,
          onlyDisplayed = false,
          persistentTagFilter = PersistentTagFilter.empty.copy(label = Some("calliopelabel"))
        )
      } yield result

      whenReady(futureTagListResultByLabel, Timeout(3.seconds)) { tags =>
        Then("I get a list with one result")
        tags.length should be(1)
        tags.count(_.label == calliope.label) should be(tags.length)
      }
    }

    scenario("Search tags by operation") {

      Given(s"a list of persisted tags: \n ${Seq(hestia, athena).mkString("\n")}")

      val futureTagListResultOperations: Future[(Seq[Tag], Seq[Tag])] = for {
        _ <- persistentOperationService.persist(fakeOperation.copy(operationId = operationIdFirst, slug = "ope-first"))
        _ <- persistentOperationService.persist(
          fakeOperation.copy(operationId = operationIdSecond, slug = "ope-second")
        )
        _ <- persistentTagTypeService.persist(tagTypeFirst)
        _ <- persistentTagTypeService.persist(tagTypeSecond)
        _ <- persistentTagService.persist(hestia)
        _ <- persistentTagService.persist(athena)
        _ <- persistentTagService.persist(ariane)
        resultFirst <- persistentTagService.find(
          start = 0,
          end = Some(10),
          sort = None,
          order = None,
          onlyDisplayed = false,
          persistentTagFilter = PersistentTagFilter.empty.copy(questionId = Some(questionFirst.questionId))
        )
        resultSecond <- persistentTagService.find(
          start = 0,
          end = Some(10),
          sort = None,
          order = None,
          onlyDisplayed = false,
          persistentTagFilter = PersistentTagFilter.empty.copy(questionId = Some(questionSecond.questionId))
        )
      } yield (resultFirst, resultSecond)

      When("I search tags by operation Id 'opefirst'")

      whenReady(futureTagListResultOperations, Timeout(3.seconds)) {
        case (resultFirst, _) =>
          Then("I get a list with two results")
          resultFirst.length should be(2)
          resultFirst.count(_.operationId.contains(operationIdFirst)) should be(resultFirst.length)
      }

      When("I search tags by operation Id 'opesecond'")

      whenReady(futureTagListResultOperations, Timeout(3.seconds)) {
        case (_, secondFirst) =>
          Then("I get a list with one result")
          secondFirst.length should be(1)
          secondFirst.count(_.operationId.contains(operationIdSecond)) should be(secondFirst.length)
      }
    }

    scenario("Search tags by tagType") {

      Given(s"a persisted tag: \n ${clio.label}")

      When(s"I search tags by tag type '${tagTypeThird.label}'")
      val futureTagListResultTagTypeFirst: Future[Seq[Tag]] = for {
        _ <- persistentTagTypeService.persist(tagTypeThird)
        _ <- persistentTagService.persist(clio)
        result <- persistentTagService.find(
          start = 0,
          end = Some(10),
          sort = None,
          order = None,
          onlyDisplayed = false,
          persistentTagFilter = PersistentTagFilter.empty.copy(tagTypeId = Some(tagTypeThird.tagTypeId))
        )
      } yield result

      whenReady(futureTagListResultTagTypeFirst, Timeout(3.seconds)) { tags =>
        Then("I get a list with three results")
        tags.length should be(1)
        tags.count(_.tagTypeId == tagTypeThird.tagTypeId) should be(tags.length)
      }
    }

    scenario("Search tags by theme") {

      Given(s"a persisted tag: \n ${thalia.label}")

      When(s"I search tags by theme '${themeIdFirst.value}'")
      val futureTagListResultThemeFirst: Future[Seq[Tag]] = for {
        _ <- persistentTagTypeService.persist(tagTypeFourth)
        _ <- persistentTagService.persist(thalia)
        result <- persistentTagService.find(
          start = 0,
          end = Some(10),
          sort = None,
          order = None,
          onlyDisplayed = false,
          persistentTagFilter = PersistentTagFilter.empty.copy(questionId = Some(questionThemeFirst.questionId))
        )
      } yield result

      whenReady(futureTagListResultThemeFirst, Timeout(3.seconds)) { tags =>
        Then("I get a list with two results")
        tags.length should be(1)
      }
    }

    scenario("Search tags by onlyDisplayed") {
      // TODO: since this test relies on data inserted by other tests,
      // there is no guarantee on the expected number of tags
      Given("a list of persisted tags")

      When("I search tags by onlyDisplayed")
      val futureTagListResultOnlyDisplayed: Future[Seq[Tag]] = for {
        result <- persistentTagService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          onlyDisplayed = true,
          persistentTagFilter = PersistentTagFilter.empty
        )
      } yield result

      whenReady(futureTagListResultOnlyDisplayed, Timeout(3.seconds)) { tags =>
        Then("I get a list with all the results")
        tags.length should be(394)
      }
    }

    scenario("Search tags by multi filters") {
      Given(s"a persisted tag: ${hera.label}")
      When(s"""I search tags by
           |tagTypeId = '${hera.tagTypeId.value}',
           |label = '${hera.label}',
           |operation = ${operationIdThird.value},
           |country = 'BR' and 
           |language = 'br' """.stripMargin)
      val futureTagListResultHera: Future[Seq[Tag]] = for {
        _ <- persistentTagTypeService.persist(tagTypeSeventh)
        _ <- persistentOperationService.persist(fakeOperation.copy(operationId = operationIdThird, slug = "ope-third"))
        _ <- persistentTagService.persist(hera)
        result <- persistentTagService.find(
          start = 0,
          end = Some(10),
          sort = None,
          order = None,
          onlyDisplayed = false,
          persistentTagFilter = PersistentTagFilter.empty
            .copy(tagTypeId = Some(hera.tagTypeId), label = Some(hera.label), questionId = hera.questionId)
        )
      } yield result

      whenReady(futureTagListResultHera, Timeout(3.seconds)) { tags =>
        Then("I get a list with one result")
        tags.length should be(1)
        tags.last should be(hera)
      }
    }
  }

  feature("count tags") {
    val fooTagType: TagType =
      TagType(tagTypeId = TagTypeId("foo"), label = "Foo", display = TagTypeDisplay.Displayed)
    val barTagType: TagType =
      TagType(tagTypeId = TagTypeId("bar"), label = "Bar", display = TagTypeDisplay.Displayed)
    val fooOperationId: OperationId = OperationId("fooOperation")
    val fooThemeId: ThemeId = ThemeId("fooTheme")

    val operationQuestion = questionForOperation(fooOperationId)
    val themeQuestion = questionForTheme(fooThemeId)

    val foo: Tag =
      targaryen.copy(
        tagId = TagId("foo"),
        tagTypeId = fooTagType.tagTypeId,
        label = "foolabel",
        operationId = Some(fooOperationId),
        questionId = Some(operationQuestion.questionId),
        country = Country("FR"),
        language = Language("fr")
      )
    val bar: Tag =
      targaryen.copy(
        tagId = TagId("bar"),
        tagTypeId = fooTagType.tagTypeId,
        label = "barlabel",
        operationId = Some(fooOperationId),
        questionId = Some(operationQuestion.questionId),
        country = Country("FR"),
        language = Language("br")
      )
    val baz: Tag =
      targaryen.copy(
        tagId = TagId("baz"),
        tagTypeId = fooTagType.tagTypeId,
        label = "bazlabel",
        questionId = Some(themeQuestion.questionId),
        country = Country("FR"),
        language = Language("fr")
      )

    scenario("Search tags by label") {

      val fooOperation = fakeOperation.copy(operationId = fooOperationId, slug = "foo-operation")
      val persistedTags: Future[Seq[Tag]] = for {
        _   <- persistentOperationService.persist(fooOperation)
        _   <- persistentQuestionService.persist(operationQuestion)
        _   <- persistentQuestionService.persist(themeQuestion)
        _   <- persistentTagTypeService.persist(fooTagType)
        _   <- persistentTagTypeService.persist(barTagType)
        foo <- persistentTagService.persist(foo)
        bar <- persistentTagService.persist(bar)
        baz <- persistentTagService.persist(baz)
      } yield Seq(foo, bar, baz)

      val tagList: Seq[String] = Seq(foo, bar, baz).map { tag =>
        s"label = ${tag.label}, tagId = ${tag.tagId.value}, operationId = ${tag.operationId.map(_.value).getOrElse("None")}"
      }

      Given(s"a list of persisted tags: \n ${tagList.mkString("\n")}")

      When("I count tags filtred")
      val futureCountTags: Future[(Int, Int, Int)] = for {
        _              <- persistedTags
        countWithLabel <- persistentTagService.count(PersistentTagFilter.empty.copy(label = Some("barlabel")))
        countWithThemeId <- persistentTagService.count(
          PersistentTagFilter.empty.copy(questionId = Some(themeQuestion.questionId))
        )
        countWithOperationId <- persistentTagService.count(
          PersistentTagFilter.empty.copy(questionId = Some(operationQuestion.questionId))
        )
      } yield (countWithLabel, countWithThemeId, countWithOperationId)

      whenReady(futureCountTags, Timeout(3.seconds)) {
        case (countWithLabel, countWithThemeId, countWithOperationId) =>
          Then("I get the count of tags")
          countWithLabel should be(1)
          countWithThemeId should be(1)
          countWithOperationId should be(2)
      }
    }
  }
}
