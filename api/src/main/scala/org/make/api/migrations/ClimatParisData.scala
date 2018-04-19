package org.make.api.migrations

import org.make.api.MakeApi
import org.make.api.migrations.InsertFixtureData.FixtureDataLine
import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.tag.TagId

import scala.concurrent.Future

object ClimatParisData extends InsertFixtureData {
  var operationId: OperationId = _
  var localRequestContext: RequestContext = _
  override def requestContext: RequestContext = localRequestContext

  override def initialize(api: MakeApi): Future[Unit] = {
    api.operationService.findOneBySlug(ClimatParisOperation.operationSlug).flatMap {
      case Some(operation) =>
        Future.successful {
          operationId = operation.operationId
          localRequestContext = RequestContext.empty.copy(operationId = Some(operationId))
        }
      case None =>
        Future.failed(
          new IllegalStateException(s"Unable to find an operation with slug ${ClimatParisOperation.operationSlug}")
        )
    }
  }

  override def extractDataLine(line: String): Option[InsertFixtureData.FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, tags, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = None,
            operation = Some(operationId),
            tags = tags.split('|').toSeq.map(TagId.apply),
            labels = Seq.empty,
            country = country,
            language = language
          )
        )
      case _ => None
    }
  }

  override val dataResource: String = "fixtures/proposals_cp.csv"
  override val runInProduction: Boolean = false
}
