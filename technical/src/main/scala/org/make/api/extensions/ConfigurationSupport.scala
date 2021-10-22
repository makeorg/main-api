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

package org.make.api.extensions

import com.typesafe.config.Config

trait ConfigurationSupport {

  protected def configuration: Config

  protected def optionalString(path: String): Option[String] = {
    if (!configuration.hasPath(path) || configuration.getIsNull(path)) {
      None
    } else {
      Some(configuration.getString(path))
    }
  }

  protected def stringWithDefault(path: String, default: String): String = {
    if (configuration.hasPath(path)) {
      configuration.getString(path)
    } else {
      default
    }
  }

  protected def optionalInt(path: String): Option[Int] = {
    if (!configuration.hasPath(path) || configuration.getIsNull(path)) {
      None
    } else {
      Some(configuration.getInt(path))
    }
  }

  protected def intWithDefault(path: String, default: Int): Int = {
    if (configuration.hasPath(path)) {
      configuration.getInt(path)
    } else {
      default
    }
  }

  protected def optionalBoolean(path: String): Option[Boolean] = {
    if (!configuration.hasPath(path) || configuration.getIsNull(path)) {
      None
    } else {
      Some(configuration.getBoolean(path))
    }
  }

  protected def booleanWithDefault(path: String, default: Boolean): Boolean = {
    if (configuration.hasPath(path)) {
      configuration.getBoolean(path)
    } else {
      default
    }
  }

  protected def optionalLong(path: String): Option[Long] = {
    if (!configuration.hasPath(path) || configuration.getIsNull(path)) {
      None
    } else {
      Some(configuration.getLong(path))
    }
  }

  protected def longWithDefault(path: String, default: Long): Long = {
    if (configuration.hasPath(path)) {
      configuration.getLong(path)
    } else {
      default
    }
  }

  protected def optionalDouble(path: String): Option[Double] = {
    if (!configuration.hasPath(path) || configuration.getIsNull(path)) {
      None
    } else {
      Some(configuration.getDouble(path))
    }
  }

  protected def doubleWithDefault(path: String, default: Double): Double = {
    if (configuration.hasPath(path)) {
      configuration.getDouble(path)
    } else {
      default
    }
  }

}
