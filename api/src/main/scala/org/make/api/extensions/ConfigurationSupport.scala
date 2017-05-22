package org.make.api.extensions

import com.typesafe.config.Config

trait ConfigurationSupport {

  protected def configuration: Config

  protected def optionalString(path: String): Option[String] = {
    if(configuration.hasPath(path)) Some(configuration.getString(path))
    else None
  }

  protected def stringWithDefault(path: String, default: String): String = {
    if(configuration.hasPath(path)) configuration.getString(path)
    else default
  }

  protected def optionalInt(path: String): Option[Int] = {
    if(configuration.hasPath(path)) Some(configuration.getInt(path))
    else None
  }

  protected def intWithDefault(path: String, default: Int): Int = {
    if(configuration.hasPath(path)) configuration.getInt(path)
    else default
  }

  protected def optionalBoolean(path: String): Option[Boolean] = {
    if(configuration.hasPath(path)) Some(configuration.getBoolean(path))
    else None
  }

  protected def booleanWithDefault(path: String, default: Boolean): Boolean = {
    if(configuration.hasPath(path)) configuration.getBoolean(path)
    else default
  }

  protected def optionalLong(path: String): Option[Long] = {
    if(configuration.hasPath(path)) Some(configuration.getLong(path))
    else None
  }

  protected def longWithDefault(path: String, default: Long): Long = {
    if(configuration.hasPath(path)) configuration.getLong(path)
    else default
  }

  protected def optionalDouble(path: String): Option[Double] = {
    if(configuration.hasPath(path)) Some(configuration.getDouble(path))
    else None
  }

  protected def doubleWithDefault(path: String, default: Double): Double = {
    if(configuration.hasPath(path)) configuration.getDouble(path)
    else default
  }

}
