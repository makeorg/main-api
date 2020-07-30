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

name := "make-core"

libraryDependencies ++= Seq(
  Dependencies.akkaPersistence,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaHttpSwagger, // TODO: import only swagger not akka-http
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.enumeratum,
  Dependencies.enumeratumCirce,
  Dependencies.enumeratumScalacheck,
  Dependencies.jsonLenses,
  Dependencies.avro4s,
  Dependencies.circeGeneric,
  Dependencies.refinedCirce,
  Dependencies.refinedScala,
  Dependencies.stamina,
  Dependencies.slugify,
  Dependencies.jsoup,
  Dependencies.refinedScalaCheck,
  Dependencies.scalaCheck
)
