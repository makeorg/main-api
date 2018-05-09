package org.make.core.sequence.indexed

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.CirceFormatters
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

object IndexedSequenceTheme {
  implicit val encoder: ObjectEncoder[IndexedSequenceTheme] = deriveEncoder[IndexedSequenceTheme]
  implicit val decoder: Decoder[IndexedSequenceTheme] = deriveDecoder[IndexedSequenceTheme]
}

case class IndexedSequenceProposalId(proposalId: ProposalId)

object IndexedSequenceProposalId {
  implicit val encoder: ObjectEncoder[IndexedSequenceProposalId] = deriveEncoder[IndexedSequenceProposalId]
  implicit val decoder: Decoder[IndexedSequenceProposalId] = deriveDecoder[IndexedSequenceProposalId]
}

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

object IndexedSequence extends CirceFormatters {
  implicit val encoder: ObjectEncoder[IndexedSequence] = deriveEncoder[IndexedSequence]
  implicit val decoder: Decoder[IndexedSequence] = deriveDecoder[IndexedSequence]
}

final case class Context(operation: Option[OperationId],
                         source: Option[String],
                         location: Option[String],
                         question: Option[String])

object Context {
  implicit val encoder: ObjectEncoder[Context] = deriveEncoder[Context]
  implicit val decoder: Decoder[Context] = deriveDecoder[Context]
}

final case class SequencesSearchResult(total: Int, results: Seq[IndexedSequence])

object SequencesSearchResult {
  implicit val encoder: ObjectEncoder[SequencesSearchResult] = deriveEncoder[SequencesSearchResult]
}

final case class IndexedStartSequence(id: SequenceId,
                                      title: String,
                                      slug: String,
                                      translation: Seq[SequenceTranslation] = Seq.empty,
                                      themes: Seq[IndexedSequenceTheme],
                                      proposals: Seq[IndexedProposal])

object IndexedStartSequence {
  implicit val encoder: ObjectEncoder[IndexedStartSequence] = deriveEncoder[IndexedStartSequence]
}
