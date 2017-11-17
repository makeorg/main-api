package org.make.core.sequence.indexed

import java.time.ZonedDateTime

import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.reference.{Tag, ThemeId, ThemeTranslation}
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
  val tags: String = "tags"
  val tagId: String = "tags.tagId"
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
                           tags: Seq[Tag],
                           themes: Seq[IndexedSequenceTheme],
                           proposals: Seq[IndexedSequenceProposalId],
                           searchable: Boolean)

final case class Context(operation: Option[String],
                         source: Option[String],
                         location: Option[String],
                         question: Option[String])

final case class SequencesSearchResult(total: Int, results: Seq[IndexedSequence])

final case class IndexedStartSequence(id: SequenceId,
                                      title: String,
                                      slug: String,
                                      translation: Seq[SequenceTranslation] = Seq.empty,
                                      tags: Seq[Tag],
                                      themes: Seq[IndexedSequenceTheme],
                                      proposals: Seq[IndexedProposal])
