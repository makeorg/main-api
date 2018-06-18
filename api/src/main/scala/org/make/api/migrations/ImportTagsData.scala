package org.make.api.migrations

import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.core.operation.OperationId
import org.make.core.tag.{TagDisplay, TagTypeId}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.Source

trait ImportTagsData extends Migration with StrictLogging {

  var operationId: OperationId = _

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def extractDataLine(line: String): Option[ImportTagsData.TagsDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(weight, label, tagType, tagDisplay, country, language) =>
        Some(
          ImportTagsData.TagsDataLine(
            label = label,
            tagTypeId = TagTypeId(tagType),
            tagDisplay = TagDisplay.matchTagDisplayOrDefault(tagDisplay),
            weight = weight.toFloat,
            country = country,
            language = language
          )
        )
      case _ => None
    }
  }

  def dataResource: String

  override def migrate(api: MakeApi): Future[Unit] = {

    val tags: Seq[ImportTagsData.TagsDataLine] =
      Source.fromResource(dataResource).getLines().toSeq.drop(1).flatMap(extractDataLine)

    api.tagService.findByOperationId(operationId).flatMap {
      case operationTags if operationTags.isEmpty =>
        sequentially(tags) { tag =>
          api.tagService
            .createTag(
              label = tag.label,
              tagTypeId = tag.tagTypeId,
              operationId = Some(operationId),
              themeId = None,
              country = tag.country,
              language = tag.language,
              display = tag.tagDisplay,
              weight = tag.weight
            )
            .flatMap(_ => Future.successful {})
        }
      case _ => Future.successful {}
    }
  }
}

object ImportTagsData {
  case class TagsDataLine(label: String,
                          tagTypeId: TagTypeId,
                          tagDisplay: TagDisplay,
                          weight: Float,
                          country: String,
                          language: String)
}
