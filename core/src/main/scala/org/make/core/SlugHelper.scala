package org.make.core

import com.github.slugify.Slugify

object SlugHelper {
  private val slugifier = new Slugify()

  def apply(value: String): String =
    slugifier.slugify(value)
}
