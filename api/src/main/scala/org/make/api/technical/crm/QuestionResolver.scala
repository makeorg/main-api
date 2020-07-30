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

package org.make.api.technical.crm

import org.make.core.question.Question
import org.make.core.operation.OperationId
import org.make.core.RequestContext
import org.make.core.reference.Country
import org.make.core.reference.Language

class QuestionResolver(questions: Seq[Question], operations: Map[String, OperationId]) {

  private val questionsWithOperation: Seq[Question] = questions.filter(_.operationId.isDefined)

  def findQuestionWithOperation(predicate: Question => Boolean): Option[Question] =
    questionsWithOperation.find(predicate)

  def extractQuestionWithOperationFromRequestContext(requestContext: RequestContext): Option[Question] = {
    requestContext.questionId
      .flatMap(questionId => questionsWithOperation.find(_.questionId == questionId))
      .orElse {
        requestContext.operationId.flatMap { operationId =>
          questionsWithOperation.find(
            question =>
              // In old operations, the header contained the slug and not the id
              // also the old operations didn't all have a country or language
              (question.operationId.contains(operationId) ||
                question.operationId == operations.get(operationId.value)) &&
                question.countries.toList.contains(requestContext.country.getOrElse(Country("FR"))) &&
                requestContext.locale.map(_.language).getOrElse(Language("fr")) == question.language
          )
        }
      }
  }
}
