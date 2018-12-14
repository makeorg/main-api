package org.make.api.question
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.PersistentOperationOfQuestionService
import org.make.api.sequence.{SequenceResult, SequenceService}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.RequestContext
import org.make.core.operation.{OperationId, OperationOfQuestion}
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar

import scala.collection.immutable.Seq
import scala.concurrent.Future

class QuestionApiTest
    extends MakeApiTestBase
    with MockitoSugar
    with DefaultQuestionApiComponent
    with QuestionServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication {
  override val questionService: QuestionService = mock[QuestionService]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]

  val routes: Route = sealRoute(questionApi.routes)

  val baseQuestion = Question(QuestionId("question-id"), "slug", Country("FR"), Language("fr"), "Slug ?", None, None)

  feature("list questions") {

    when(questionService.countQuestion(any[SearchQuestionRequest])).thenReturn(Future.successful(42))
    when(questionService.searchQuestion(any[SearchQuestionRequest])).thenReturn(Future.successful(Seq(baseQuestion)))

    val uri = "/moderation/questions?start=0&end=1&operationId=foo&country=FR&language=fr"

    scenario("list questions") {
      Get(uri) ~> routes ~> check {
        status should be(StatusCodes.OK)
        header("x-total-count").isDefined shouldBe true
        val questions: Seq[ModerationQuestionResponse] = entityAs[Seq[ModerationQuestionResponse]]
        questions.size should be(1)
        questions.head.id.value should be(baseQuestion.questionId.value)
      }
    }
  }

  feature("start sequence by question id") {
    val baseOperationOfQuestion = OperationOfQuestion(
      QuestionId("question-id"),
      OperationId("foo-operation-id"),
      None,
      None,
      "Foo operation",
      SequenceId("sequence-id")
    )
    scenario("valid question") {
      when(persistentOperationOfQuestionService.getById(any[QuestionId]))
        .thenReturn(Future.successful(Some(baseOperationOfQuestion)))
      when(
        sequenceService.startNewSequence(
          maybeUserId = ArgumentMatchers.any[Option[UserId]],
          sequenceId = ArgumentMatchers.any[SequenceId],
          includedProposals = ArgumentMatchers.any[Seq[ProposalId]],
          tagsIds = ArgumentMatchers.any[Option[Seq[TagId]]],
          requestContext = ArgumentMatchers.any[RequestContext]
        )
      ).thenReturn(Future.successful(Some(SequenceResult(SequenceId("sequence-id"), "title", "slug", Seq.empty))))
      Post("/questions/question-id/start-sequence").withEntity(HttpEntity(ContentTypes.`application/json`, "{}")) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    scenario("invalid question") {
      when(persistentOperationOfQuestionService.getById(any[QuestionId]))
        .thenReturn(Future.successful(None))
      Post("/questions/question-id/start-sequence").withEntity(HttpEntity(ContentTypes.`application/json`, "{}")) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

}
