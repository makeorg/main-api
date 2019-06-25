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

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import org.make.api.MakeUnitTest
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.question.{PersistentQuestionService, PersistentQuestionServiceComponent}
import org.make.api.tag.{PersistentTagService, PersistentTagServiceComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference._
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class OperationServiceTest
    extends MakeUnitTest
    with DefaultOperationServiceComponent
    with PersistentTagServiceComponent
    with PersistentQuestionServiceComponent
    with IdGeneratorComponent
    with MakeDBExecutionContextComponent
    with PersistentOperationServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val persistentOperationService: PersistentOperationService = mock[PersistentOperationService]
  override lazy val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]

  override def writeExecutionContext: ExecutionContext = mock[ExecutionContext]
  override def readExecutionContext: ExecutionContext = mock[ExecutionContext]

  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()

  val fooOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("foo"),
    slug = "first-operation",
    defaultLanguage = Language("fr"),
    allowedSources = Seq.empty,
    operationKind = OperationKind.PublicConsultation,
    events = List(
      OperationAction(
        date = now,
        makeUserId = userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    questions = Seq(
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId("foo1"),
          country = Country("BR"),
          language = Language("fr"),
          slug = "foo-BR",
          question = "foo BR?",
          operationId = Some(OperationId("foo")),
          themeId = None
        ),
        details = OperationOfQuestion(
          questionId = QuestionId("foo1"),
          operationId = OperationId("foo"),
          startDate = None,
          endDate = None,
          operationTitle = "première operation",
          landingSequenceId = SequenceId("first-sequence-id-BR"),
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
          description = OperationOfQuestion.defaultDescription
        )
      ),
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId("foo2"),
          country = Country("GB"),
          language = Language("en"),
          slug = "foo-GB",
          question = "foo GB?",
          operationId = Some(OperationId("foo")),
          themeId = None
        ),
        details = OperationOfQuestion(
          questionId = QuestionId("foo2"),
          operationId = OperationId("foo"),
          startDate = None,
          endDate = None,
          operationTitle = "first operation",
          landingSequenceId = SequenceId("first-sequence-id-GB"),
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
          description = OperationOfQuestion.defaultDescription
        )
      )
    )
  )

  val fooTag = Tag(
    tagId = TagId("fooTag"),
    label = "foo",
    display = TagDisplay.Displayed,
    tagTypeId = TagTypeId("tagType"),
    weight = 1,
    themeId = Some(ThemeId("fooTheme")),
    operationId = None,
    country = Country("GB"),
    language = Language("en"),
    questionId = None
  )

  feature("find operations") {
    scenario("find operations and get the right tags") {
      Given("a list of operations")
      When("fetch this list")
      Then("tags are fetched from persistent service")

      Mockito
        .when(persistentOperationService.find(any[Option[String]], any[Option[Country]], any[Option[LocalDate]]))
        .thenReturn(Future.successful(Seq(fooOperation)))

      Mockito
        .when(persistentTagService.findByQuestion(ArgumentMatchers.eq(QuestionId("foo-question"))))
        .thenReturn(Future.successful(Seq(fooTag)))

      val futureOperations: Future[Seq[Operation]] =
        operationService.find(slug = None, country = None, maybeSource = None, openAt = None)

      whenReady(futureOperations, Timeout(3.seconds)) { operations =>
        logger.debug(operations.map(_.toString).mkString(", "))
//        val fooOperation: Operation = operations.filter(operation => operation.operationId.value == "foo").head
//        fooOperation.questions.filter(cc => cc.question.country == Country("GB")).head.tagIds.size shouldBe 1
      }
    }

  }

}
