package org.make.api.migrations

import org.make.api.MakeApi

import scala.concurrent.Future

object CultureImportTagsData extends ImportTagsData {

  override def initialize(api: MakeApi): Future[Unit] = {
    for {
      maybeOperation <- api.operationService.findOneBySlug(CultureOperation.operationSlug)
    } yield
      maybeOperation match {
        case Some(operation) =>
          operationId = operation.operationId
        case None =>
          throw new IllegalStateException(
            s"Unable to find an operation with slug ${CultureOperation.operationSlug}"
          )
      }
  }

  override val dataResource: String = "fixtures/tags_culture.csv"
  override val runInProduction: Boolean = true
}





