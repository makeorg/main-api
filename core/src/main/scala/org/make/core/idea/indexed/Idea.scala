package org.make.core.idea.indexed

import java.time.ZonedDateTime

import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.CirceFormatters
import org.make.core.idea.{Idea, IdeaId, IdeaStatus}
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId

object IdeaElasticsearchFieldNames {
  val ideaId: String = "ideaId"
  val name: String = "name"
  val nameFr: String = "name.fr"
  val nameEn: String = "name.en"
  val nameIt: String = "name.it"
  val nameGeneral: String = "name.general"
  val operationId: String = "operationId"
  val themeId: String = "themeId"
  val question: String = "question"
  val country: String = "country"
  val language: String = "language"
  val status: String = "status"
  val createdAt: String = "createdAt"
  val updatedAt: String = "updatedAt"
}

case class IndexedIdea(ideaId: IdeaId,
                       name: String,
                       operationId: Option[OperationId],
                       themeId: Option[ThemeId],
                       question: Option[String],
                       country: Option[String],
                       language: Option[String],
                       status: IdeaStatus,
                       createdAt: ZonedDateTime,
                       updatedAt: Option[ZonedDateTime])

object IndexedIdea extends CirceFormatters {
  implicit val encoder: ObjectEncoder[IndexedIdea] = deriveEncoder[IndexedIdea]
  implicit val decoder: Decoder[IndexedIdea] = deriveDecoder[IndexedIdea]

  def createFromIdea(idea: Idea): IndexedIdea = {
    IndexedIdea(
      ideaId = idea.ideaId,
      name = idea.name,
      operationId = idea.operationId,
      themeId = idea.themeId,
      question = idea.question,
      country = idea.country,
      language = idea.language,
      status = idea.status,
      createdAt = idea.createdAt.get,
      updatedAt = idea.updatedAt
    )
  }
}

final case class IdeaSearchResult(total: Int, results: Seq[IndexedIdea])

object IdeaSearchResult {
  implicit val encoder: ObjectEncoder[IdeaSearchResult] = deriveEncoder[IdeaSearchResult]
  implicit val decoder: Decoder[IdeaSearchResult] = deriveDecoder[IdeaSearchResult]

  def empty: IdeaSearchResult = IdeaSearchResult(0, Seq.empty)
}
