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

package org.make.api.operation

import java.time.ZonedDateTime

import eu.timepit.refined.auto._
import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentOperationOfQuestionServiceIT
    extends DatabaseTest
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40011

  val now: ZonedDateTime = DateHelper.now()
  val sequenceIdFR: SequenceId = idGenerator.nextSequenceId()
  val sequenceIdGB: SequenceId = idGenerator.nextSequenceId()
  def questionId: QuestionId = idGenerator.nextQuestionId()
  def operationId: OperationId = idGenerator.nextOperationId()

  def generateOperationOfQuestion: OperationOfQuestion = OperationOfQuestion(
    operationId = operationId,
    questionId = questionId,
    startDate = None,
    endDate = None,
    operationTitle = "title",
    landingSequenceId = sequenceIdFR,
    canPropose = true,
    sequenceCardsConfiguration = SequenceCardsConfiguration(
      introCard = IntroCard(enabled = true, title = None, description = None),
      pushProposalCard = PushProposalCard(enabled = true),
      signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
      finalCard = FinalCard(
        enabled = true,
        sharingEnabled = false,
        title = None,
        shareDescription = None,
        learnMoreTitle = None,
        learnMoreTextButton = None,
        linkUrl = None
      )
    ),
    aboutUrl = None,
    metas = Metas(title = None, description = None, picture = None),
    theme = QuestionTheme.default,
    description = OperationOfQuestion.defaultDescription,
    consultationImage = Some("https://example.com/image"),
    consultationImageAlt = Some("alt for image"),
    descriptionImage = Some("https://example.com/descriptionImage"),
    descriptionImageAlt = Some("alt for descriptionImage"),
    displayResults = false,
    resultsLink = None,
    proposalsCount = 42,
    participantsCount = 84,
    actions = None,
    featured = true
  )

  def createOperationOfQuestion(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
    val question = Question(
      questionId = operationOfQuestion.questionId,
      slug = s"slug-question-${operationOfQuestion.questionId.value}",
      country = Country("FR"),
      language = Language("fr"),
      question = "Question ?",
      shortTitle = None,
      operationId = None
    )
    val simpleOperation = SimpleOperation(
      operationId = operationOfQuestion.operationId,
      status = OperationStatus.Active,
      slug = s"slug-operation-${operationOfQuestion.operationId.value}",
      allowedSources = Seq.empty,
      defaultLanguage = Language("fr"),
      operationKind = OperationKind.PublicConsultation,
      createdAt = None,
      updatedAt = None
    )

    for {
      _      <- persistentQuestionService.persist(question)
      _      <- persistentOperationService.persist(simpleOperation)
      result <- persistentOperationOfQuestionService.persist(operationOfQuestion)
    } yield result
  }

  feature("An operationOfQuestion can be persisted") {
    scenario("Persist an operationOfQuestion and retrieve it") {
      val baseOperationOfQuestion = generateOperationOfQuestion

      val futureOperationOfQuestion: Future[Option[OperationOfQuestion]] = for {
        _      <- createOperationOfQuestion(baseOperationOfQuestion)
        result <- persistentOperationOfQuestionService.getById(baseOperationOfQuestion.questionId)
      } yield result

      whenReady(futureOperationOfQuestion, Timeout(3.seconds)) { operationOfQuestion =>
        operationOfQuestion.isDefined shouldBe true
        operationOfQuestion.map(_.questionId) shouldBe Some(baseOperationOfQuestion.questionId)
        operationOfQuestion.map(_.operationId) shouldBe Some(baseOperationOfQuestion.operationId)
        operationOfQuestion.map(_.canPropose) shouldBe Some(true)
        operationOfQuestion.map(_.sequenceCardsConfiguration.introCard.enabled) shouldBe Some(true)
        operationOfQuestion.map(_.sequenceCardsConfiguration.finalCard.sharingEnabled) shouldBe Some(false)
        operationOfQuestion.map(_.proposalsCount) shouldBe Some(42)
        operationOfQuestion.map(_.participantsCount) shouldBe Some(84)
        operationOfQuestion.map(_.featured) shouldBe Some(true)
      }
    }
  }

  feature("search operation of question") {
    scenario("no filter") {
      val operationOfQuestion1 = generateOperationOfQuestion
      val operationOfQuestion2 = generateOperationOfQuestion

      val futureOperationOfQuestion: Future[Seq[OperationOfQuestion]] = for {
        _      <- createOperationOfQuestion(operationOfQuestion1)
        _      <- createOperationOfQuestion(operationOfQuestion2)
        result <- persistentOperationOfQuestionService.search(0, None, None, None, None, None, None, None)
      } yield result

      whenReady(futureOperationOfQuestion, Timeout(3.seconds)) { operationOfQuestion =>
        operationOfQuestion.size shouldBe 3
      }
    }

    scenario("questionIds filter") {
      val operationOfQuestion3 = generateOperationOfQuestion.copy(questionId = QuestionId("toBeFiltered"))

      val futureOperationOfQuestion: Future[Seq[OperationOfQuestion]] = for {
        _ <- createOperationOfQuestion(operationOfQuestion3)
        result <- persistentOperationOfQuestionService.search(
          0,
          None,
          None,
          None,
          Some(Seq(QuestionId("toBeFiltered"))),
          None,
          None,
          None
        )
      } yield result

      whenReady(futureOperationOfQuestion, Timeout(3.seconds)) { operationOfQuestion =>
        operationOfQuestion.size shouldBe 1
      }
    }
  }

  feature("Find an operationOfQuestion by operation") {
    scenario("Persist an operationOfQuestion and find it by its operationId") {
      val baseOperationOfQuestion = generateOperationOfQuestion

      val futureOperationOfQuestion: Future[Seq[OperationOfQuestion]] = for {
        _      <- createOperationOfQuestion(baseOperationOfQuestion)
        result <- persistentOperationOfQuestionService.find(Some(baseOperationOfQuestion.operationId))
      } yield result

      whenReady(futureOperationOfQuestion, Timeout(3.seconds)) { operationOfQuestion =>
        operationOfQuestion shouldBe a[Seq[_]]
        operationOfQuestion.size shouldBe 1
        operationOfQuestion.head.questionId shouldBe baseOperationOfQuestion.questionId
        operationOfQuestion.head.operationId shouldBe baseOperationOfQuestion.operationId
      }
    }
  }

  feature("Modify an operationOfQuestion") {
    scenario("Persist an operationOfQuestion and modify it") {
      val baseOperationOfQuestion = generateOperationOfQuestion

      val futureOperationOfQuestion: Future[Option[OperationOfQuestion]] = for {
        _ <- createOperationOfQuestion(baseOperationOfQuestion)
        _ <- persistentOperationOfQuestionService.modify(
          baseOperationOfQuestion
            .copy(
              operationTitle = s"${baseOperationOfQuestion.operationTitle} modified",
              canPropose = false,
              sequenceCardsConfiguration =
                baseOperationOfQuestion.sequenceCardsConfiguration.copy(pushProposalCard = PushProposalCard(false)),
              theme = baseOperationOfQuestion.theme.copy(color = "#424242"),
              proposalsCount = 420,
              participantsCount = 840,
              actions = Some("some actions")
            )
        )
        result <- persistentOperationOfQuestionService.getById(baseOperationOfQuestion.questionId)
      } yield result

      whenReady(futureOperationOfQuestion, Timeout(3.seconds)) { operationOfQuestion =>
        operationOfQuestion.isDefined shouldBe true
        operationOfQuestion.map(_.questionId) shouldBe Some(baseOperationOfQuestion.questionId)
        operationOfQuestion.map(_.operationId) shouldBe Some(baseOperationOfQuestion.operationId)
        operationOfQuestion.map(_.operationTitle) shouldBe Some(s"${baseOperationOfQuestion.operationTitle} modified")
        operationOfQuestion.map(_.canPropose) shouldBe Some(false)
        operationOfQuestion.map(_.sequenceCardsConfiguration.pushProposalCard.enabled) shouldBe Some(false)
        operationOfQuestion.map(_.theme.color) shouldBe Some("#424242")
        operationOfQuestion.map(_.proposalsCount) shouldBe Some(420)
        operationOfQuestion.map(_.participantsCount) shouldBe Some(840)
        operationOfQuestion.flatMap(_.actions) shouldBe Some("some actions")
      }
    }
  }

  feature("questionIdFromSequenceId") {
    scenario("existing sequenceId") {
      val sequenceId = SequenceId("existing sequenceId")
      val questionId = QuestionId("existing sequenceId")
      val operationId = OperationId("existing sequenceId")
      val future =
        (for {
          _ <- persistentQuestionService.persist(
            Question(questionId, "existing-sequence-id", Country("FR"), Language("fr"), "", None, None)
          )
          _ <- persistentOperationService.persist(
            SimpleOperation(
              operationId,
              OperationStatus.Active,
              "-my-slug",
              Seq.empty,
              Language("fr"),
              OperationKind.PublicConsultation,
              Some(DateHelper.now()),
              Some(DateHelper.now())
            )
          )
          _ <- persistentOperationOfQuestionService.persist(
            operationOfQuestion(questionId = questionId, landingSequenceId = sequenceId, operationId = operationId)
          )
          maybeQuestion <- persistentOperationOfQuestionService.questionIdFromSequenceId(sequenceId)
        } yield maybeQuestion)
      whenReady(future, Timeout(10.seconds))(_ should contain(questionId))
    }

    scenario("unknown sequenceId") {
      whenReady(
        persistentOperationOfQuestionService.questionIdFromSequenceId(SequenceId("unknown-sequence")),
        Timeout(10.seconds)
      ) {
        _ should be(None)
      }
    }

  }
}
