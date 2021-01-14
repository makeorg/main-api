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

import java.io.IOException

import caliban.CalibanError.ExecutionError
import caliban.InputValue
import caliban.Value.{EnumValue, StringValue => CalibanString}
import caliban.introspection.adt.{__EnumValue, __Type, __TypeKind}
import caliban.schema.{ArgBuilder, PureStep, Schema, Step}
import caliban.wrappers.Wrapper.OverallWrapper
import grizzled.slf4j.Logging
import enumeratum.values.{NoSuchMember, StringEnum, StringEnumEntry, ValueEnumEntry}
import org.make.core.StringValue
import zio.console.Console
import zio.query.Request
import zio.{IO, UIO, ULayer, ZLayer}

object GraphQLUtils extends Logging {
  lazy val debugPrint: OverallWrapper[Console] =
    OverallWrapper { process => request =>
      IO.effectTotal(logger.debug(request.toString)) *> process(request)
    }

  object ConsoleLoggingService {
    val live: ULayer[Console] =
      ZLayer.succeed(new Console.Service {
        override def putStr(line: String): UIO[Unit] =
          IO.succeed(logger.info(line))

        override def putStrErr(line: String): UIO[Unit] =
          IO.succeed(logger.error(line))

        override def putStrLn(line: String): UIO[Unit] =
          IO.succeed(logger.info(line))

        override def putStrLnErr(line: String): UIO[Unit] =
          IO.succeed(logger.error(line))

        override val getStrLn: IO[IOException, String] =
          IO.fail(new IOException("not supported"))
      })
  }

  implicit def stringValueStringSchema[T <: StringValue]: Schema[Any, T] =
    Schema.stringSchema.contramap(_.value)

  implicit def enumSchema[T <: StringEnumEntry](implicit helper: StringEnum[T]): Schema[Any, T] =
    new Schema[Any, T] {
      private def sanitizeName(name: String): String = {
        if (name.endsWith("$")) {
          name.dropRight(1).mkString
        } else {
          name
        }
      }

      override protected[this] def toType(isInput: Boolean, isSubscription: Boolean): __Type = __Type(
        kind = __TypeKind.ENUM,
        name = Some(sanitizeName(helper.getClass.getSimpleName)),
        description = None,
        fields = _      => None,
        interfaces = () => None,
        possibleTypes = None,
        enumValues = _ =>
          Some(
            helper.values
              .map(v => __EnumValue(name = v.value, description = None, isDeprecated = false, deprecationReason = None))
              .toList
          ),
        inputFields = None,
        ofType = None,
        directives = None,
        origin = None
      )
      override def resolve(value: T): Step[Any] = PureStep(EnumValue(value.value))

    }

  implicit def enumArgBuilder[T <: StringEnumEntry](implicit helper: StringEnum[T]): ArgBuilder[T] = {
    def parseEnumValue(value: String): Either[ExecutionError, T] = {
      val parsedValue: Either[NoSuchMember[String, ValueEnumEntry[String]], T] =
        helper.withValueEither(value)

      parsedValue.left.map { e =>
        ExecutionError(s"${e.notFoundValue} is not a valid value, use ${e.enumValues.mkString(", ")}")
      }
    }

    (input: InputValue) => {
      input match {
        case EnumValue(value)     => parseEnumValue(value)
        case CalibanString(value) => parseEnumValue(value)
        case other                => Left(ExecutionError(s"cannot parse $other"))
      }
    }

  }

}

trait IdsRequest[F[_], T <: StringValue, U] extends Request[Throwable, F[U]] {
  val ids: F[T]
}
