package org.make.api.technical

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Origin
import com.typesafe.scalalogging.StrictLogging
import kamon.akka.http.AkkaHttp.OperationNameGenerator

class MakeOperationNameGenerator extends OperationNameGenerator with StrictLogging {

  logger.info("creating make name generator for akka-http")

  override def serverOperationName(request: HttpRequest): String = {
    originFromHeaders(request).map(origin => "origin-" + origin).getOrElse("origin-unknown")
  }

  // Copied from kamon-akka-http
  override def clientOperationName(request: HttpRequest): String = {
    request.uri.copy(rawQueryString = None, fragment = None).toString()
  }

  private def originFromHeaders(request: HttpRequest): Option[String] = {
    request.header[Origin].flatMap(_.origins.headOption.map(_.host.host.address()))
  }

}
