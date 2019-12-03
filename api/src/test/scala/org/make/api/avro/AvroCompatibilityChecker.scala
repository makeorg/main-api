/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
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

package org.make.api.avro

import com.typesafe.scalalogging.StrictLogging
import io.confluent.kafka.schemaregistry.avro.AvroCompatibilityLevel
import org.apache.avro.Schema
import org.make.core.AvroSerializers

import scala.util.{Failure, Success, Try}

object AvroCompatibilityChecker extends App with AvroSerializers with StrictLogging {

  def isCompatible(newSchema: Schema, currentSchema: Schema): Boolean = {
    AvroCompatibilityLevel.BACKWARD.compatibilityChecker.isCompatible(newSchema, currentSchema)
  }

  def loadSchemas(className: String): Seq[Schema] = {
    var i = 1
    var accumulator: Seq[Schema] = Seq.empty
    var currentSchema = loadSchema(className, i)
    accumulator ++= currentSchema.toSeq

    while (currentSchema.isDefined) {
      i += 1
      currentSchema = loadSchema(className, i)
      accumulator ++= currentSchema.toSeq
    }
    accumulator
  }

  private def loadSchema(className: String, version: Int): Option[Schema] = {
    Option(Thread.currentThread().getContextClassLoader.getResourceAsStream(s"avro/$className-v$version.avro")).map {
      stream =>
        Try(new Schema.Parser().parse(stream)) match {
          case Success(value) => value
          case Failure(exception) =>
            logger.error(s"Error while loading definition $className with version $version", exception)
            throw exception
        }
    }
  }

}
