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

package org.make.api.technical

import enumeratum.values.{StringEnum, StringEnumEntry}
import eu.timepit.refined.api.{RefType, Refined, Validate}
import eu.timepit.refined.refineV
import org.make.core.StringValue
import scalikejdbc.{Binders, ParameterBinderFactory, TypeBinder}

object ScalikeSupport {

  implicit def stringEnumBinders[A <: StringEnumEntry](implicit enum: StringEnum[A]): Binders[A] =
    Binders.string.xmap(enum.withValue, _.value)

  implicit def stringEnumEntryParameterBinderFactory[A <: StringEnumEntry, B <: A]: ParameterBinderFactory[B] =
    ParameterBinderFactory.stringParameterBinderFactory.contramap(_.value)

  implicit def stringValueParameterBinderFactory[A <: StringValue]: ParameterBinderFactory[A] =
    ParameterBinderFactory.stringParameterBinderFactory.contramap(_.value)

  /*
   * The following code is a copy-paste from https://github.com/katainaka0503/scalikejdbc-refined
   **/
  implicit def refinedParameterBinderFactory[T, P, F[_, _]](
    implicit
    based: ParameterBinderFactory[T],
    refType: RefType[F]
  ): ParameterBinderFactory[F[T, P]] =
    based.contramap(refType.unwrap)

  implicit def refinedTypeBinder[T, P](
    implicit validate: Validate[T, P],
    based: TypeBinder[T]
  ): TypeBinder[Refined[T, P]] =
    based.map(refineV[P].unsafeFrom(_))
}
