package org.make.api.technical

import java.util.UUID

import akka.http.scaladsl.model.headers.CustomHeader
import akka.http.scaladsl.server.{Directive0, Directives}
import de.knutwalker.akka.http.support.CirceHttpSupport
import kamon.akka.http.KamonTraceDirectives
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.CirceFormatters

trait MakeDirectives
    extends Directives
    with KamonTraceDirectives
    with CirceHttpSupport
    with CirceFormatters
    with MakeAuthentication { this: MakeDataHandlerComponent =>

  def makeTrace(name: String, tags: Map[String, String] = Map.empty): Directive0 = {
    val requestId: String = UUID.randomUUID().toString
    val startTime = System.currentTimeMillis()

    traceName(name, tags + ("id" -> requestId))

    mapResponse { response =>
      response.withHeaders(RequestIdHeader(requestId), RouteNameHeader(name), RequestTimeHeader(startTime))
    }
  }

  class RequestIdHeader(override val value: String) extends CustomHeader {
    override val name: String = "x-request-id"
    override val renderInRequests: Boolean = true
    override val renderInResponses: Boolean = true
  }

  object RequestIdHeader {
    def apply(id: String): RequestIdHeader = new RequestIdHeader(id)
  }

  class RouteNameHeader(override val value: String) extends CustomHeader {
    override val name: String = "x-route-name"
    override val renderInRequests: Boolean = true
    override val renderInResponses: Boolean = true
  }

  object RouteNameHeader {
    def apply(routeName: String): RouteNameHeader = new RouteNameHeader(routeName)
  }

  class RequestTimeHeader(override val value: String) extends CustomHeader {
    override val name: String = "x-route-time"
    override val renderInRequests: Boolean = true
    override val renderInResponses: Boolean = true
  }

  object RequestTimeHeader {
    def apply(startTimeMillis: Long): RequestTimeHeader =
      new RequestTimeHeader((System.currentTimeMillis() - startTimeMillis).toString)
  }

}
