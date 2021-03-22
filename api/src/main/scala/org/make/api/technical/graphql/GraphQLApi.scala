/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.graphql

import akka.http.scaladsl.server
import akka.http.scaladsl.server.{Directives, Route}
import caliban.AkkaHttpAdapter.ContextWrapper
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.{CalibanError, GraphQLInterpreter}
import org.make.api.ActorSystemComponent
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.graphql.GraphQLRuntimeComponent.{EnvType, RuntimeType}
import org.make.api.technical.MakeDirectives
import org.make.core.RequestContext
import zio.clock.Clock
import zio.internal.Platform
import zio.{FiberRef, Runtime, URIO, ZIO, ZLayer}

trait GraphQLApi extends Directives {

  def form: Route
  def graphQL: Route
  def graphQLWebSocket: Route

  def routes: Route = form ~ graphQL ~ graphQLWebSocket
}

trait GraphQLApiComponent {
  def graphQLApi: GraphQLApi
}

trait DefaultGraphQLApiComponent extends GraphQLApiComponent with MakeDirectives {
  self: MakeDirectivesDependencies with GraphQLRuntimeComponent with ActorSystemComponent =>

  override lazy val graphQLApi: GraphQLApi = new DefaultGraphQLApi

  class DefaultGraphQLApi extends GraphQLApi with AkkaHttpCirceAdapter with Directives {

    implicit private val runtime: Runtime[RuntimeType] =
      Runtime.unsafeFromLayer(
        ZLayer.fromEffect(FiberRef.make(RequestContext.empty)) ++ GraphQLUtils.ConsoleLoggingService.live ++ Clock.live,
        Platform.default
      )

    private val interpreter: GraphQLInterpreter[RuntimeType, CalibanError] =
      runtime.unsafeRun(graphQLRuntime.graphQLService.interpreter)

    override def form: Route = path("graphiql") {
      getFromResource("graphiql.html")
    }

    override def graphQL: Route = path("api" / "graphql") {
      extractExecutionContext { implicit executionContext =>
        makeOperation("graphQL") { requestContext =>
          adapter.makeHttpService(interpreter, contextWrapper = new RequestContextWrapper(requestContext))
        }
      }
    }

    override def graphQLWebSocket: Route = path("ws" / "graphql") {
      extractExecutionContext { implicit executionContext =>
        makeOperation("graphQL") { requestContext =>
          adapter.makeWebSocketService(interpreter, contextWrapper = new RequestContextWrapper(requestContext))
        }
      }
    }
  }
}

class RequestContextWrapper[T](context: RequestContext) extends ContextWrapper[RuntimeType, T] {

  override def apply[R1 <: RuntimeType, A1 >: T](ctx: server.RequestContext)(effect: URIO[R1, A1]): URIO[R1, A1] = {
    ZIO.accessM[RuntimeType](_.get[EnvType].set(context)) *> effect
  }
}
