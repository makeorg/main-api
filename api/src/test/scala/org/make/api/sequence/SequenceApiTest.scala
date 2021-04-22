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

package org.make.api.sequence

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.keyword.{KeywordService, KeywordServiceComponent}
import org.make.api.operation.{OperationOfQuestionSearchEngine, OperationOfQuestionSearchEngineComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.sequence.SequenceBehaviour._
import org.make.api.technical.auth.MakeAuthentication
import org.make.core.RequestContext
import org.make.core.proposal.indexed.Zone
import org.make.core.proposal.{ProposalId, ProposalKeywordKey}
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.collection.immutable.Seq
import scala.concurrent.Future

class SequenceApiTest
    extends MakeApiTestBase
    with DefaultSequenceApiComponent
    with SequenceServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with QuestionServiceComponent
    with KeywordServiceComponent
    with MakeAuthentication {

  override val sequenceService: SequenceService = mock[SequenceService]
  override val questionService: QuestionService = mock[QuestionService]
  override val keywordService: KeywordService = mock[KeywordService]
  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]

  val routes: Route = sealRoute(sequenceApi.routes)

  val questionId: QuestionId = QuestionId("question-id")
  val fakeQuestionId: QuestionId = QuestionId("fake")
  val includedProposalsIds: Seq[ProposalId] = Seq(ProposalId("p-id-1"))
  val requestContext: RequestContext = RequestContext.empty

  when(questionService.getQuestion(questionId)).thenReturn(Future.successful(Some(question(questionId))))
  when(questionService.getQuestion(fakeQuestionId)).thenReturn(Future.successful(None))
  when(questionService.getQuestionByQuestionIdValueOrSlug(questionId.value))
    .thenReturn(Future.successful(Some(question(questionId))))
  when(questionService.getQuestionByQuestionIdValueOrSlug(fakeQuestionId.value)).thenReturn(Future.successful(None))
  when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId)).thenReturn(Future.successful(None))
  when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(fakeQuestionId))
    .thenReturn(Future.successful(None))

  when(
    sequenceService.startNewSequence(
      behaviourParam = any[Unit],
      maybeUserId = any[Option[UserId]],
      questionId = eqTo(questionId),
      includedProposalsIds = any[Seq[ProposalId]],
      requestContext = any[RequestContext]
    )(any[SequenceBehaviourProvider[Unit]])
  ).thenReturn(Future.successful(SequenceResult(Seq.empty)))

  when(
    sequenceService.startNewSequence[ConsensusParam](
      behaviourParam = any[ConsensusParam],
      maybeUserId = any[Option[UserId]],
      questionId = eqTo(questionId),
      includedProposalsIds = any[Seq[ProposalId]],
      requestContext = any[RequestContext]
    )(any[SequenceBehaviourProvider[ConsensusParam]])
  ).thenReturn(Future.successful(SequenceResult(Seq.empty)))

  when(
    sequenceService.startNewSequence[Zone.Controversy.type](
      behaviourParam = any[Zone.Controversy.type],
      maybeUserId = any[Option[UserId]],
      questionId = eqTo(questionId),
      includedProposalsIds = any[Seq[ProposalId]],
      requestContext = any[RequestContext]
    )(any[SequenceBehaviourProvider[Zone.Controversy.type]])
  ).thenReturn(Future.successful(SequenceResult(Seq.empty)))

  when(
    sequenceService.startNewSequence[ProposalKeywordKey](
      behaviourParam = any[ProposalKeywordKey],
      maybeUserId = any[Option[UserId]],
      questionId = eqTo(questionId),
      includedProposalsIds = any[Seq[ProposalId]],
      requestContext = any[RequestContext]
    )(any[SequenceBehaviourProvider[ProposalKeywordKey]])
  ).thenReturn(Future.successful(SequenceResult(Seq.empty)))

  when(
    sequenceService.startNewSequence[Seq[TagId]](
      behaviourParam = any[Seq[TagId]],
      maybeUserId = any[Option[UserId]],
      questionId = eqTo(questionId),
      includedProposalsIds = any[Seq[ProposalId]],
      requestContext = any[RequestContext]
    )(any[SequenceBehaviourProvider[Seq[TagId]]])
  ).thenReturn(Future.successful(SequenceResult(Seq.empty)))

  Seq("standard", "consensus", "controversy", "tags").foreach { sequenceType =>
    Feature(s"start $sequenceType sequence") {
      Scenario("valid question") {
        Get(s"/sequences/$sequenceType/question-id") ~> routes ~> check {
          status should be(StatusCodes.OK)
        }
      }

      Scenario("invalid question") {
        Get(s"/sequences/$sequenceType/fake") ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature(s"start keyword sequence") {
    when(keywordService.get("keyword-key", questionId))
      .thenReturn(Future.successful(Some(keyword(questionId, "keyword-key"))))
    when(keywordService.get("fake-keyword", questionId)).thenReturn(Future.successful(None))

    Scenario("valid question and keyword") {
      Get("/sequences/keyword/question-id/keyword-key") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("valid question and invalid keyword") {
      Get("/sequences/keyword/question-id/fake-keyword") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("invalid question") {
      Get("/sequences/keyword/fake/whatever-keyword") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
