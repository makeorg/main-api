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

package org.make.core.sequence.indexed

import java.time.ZonedDateTime

import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.reference.{ThemeId, ThemeTranslation}
import org.make.core.sequence._

import scala.concurrent.Future

object SequenceElasticsearchFieldNames {
  val sequenceId: String = "id"
  val title: String = "content"
  val slug: String = "slug"
  val translation: String = "translation"
  val status: String = "status"
  val createdAt: String = "createdAt"
  val updatedAt: String = "updatedAt"
  val themes: String = "themes"
  val themeId: String = "themes.themeId"
  val themeTranslation: String = "themes.translation"
  val themeTranslationTitle: String = "themes.translation.title"
  val themeTranslationLanguage: String = "themes.translation.language"
  val proposalIds: String = "proposal.proposalId"
  val contextOperation: String = "context.operation"
  val contextSource: String = "context.source"
  val contextLocation: String = "context.location"
  val contextQuestion: String = "context.question"
  val operationId: String = "operationId"
  val searchable: String = "searchable"
}

case class SequencesResult(total: Future[Int], results: Future[Seq[IndexedSequence]])

case class IndexedSequenceTheme(themeId: ThemeId, translation: Seq[ThemeTranslation])

case class IndexedSequenceProposalId(proposalId: ProposalId)

case class IndexedSequence(id: SequenceId,
                           title: String,
                           slug: String,
                           translation: Seq[SequenceTranslation] = Seq.empty,
                           status: SequenceStatus,
                           createdAt: ZonedDateTime,
                           updatedAt: ZonedDateTime,
                           context: Option[Context],
                           themes: Seq[IndexedSequenceTheme],
                           operationId: Option[OperationId],
                           proposals: Seq[IndexedSequenceProposalId],
                           searchable: Boolean)

final case class Context(operation: Option[OperationId],
                         source: Option[String],
                         location: Option[String],
                         question: Option[String])

final case class SequencesSearchResult(total: Long, results: Seq[IndexedSequence])

final case class IndexedStartSequence(id: SequenceId,
                                      title: String,
                                      slug: String,
                                      translation: Seq[SequenceTranslation] = Seq.empty,
                                      themes: Seq[IndexedSequenceTheme],
                                      proposals: Seq[IndexedProposal])
