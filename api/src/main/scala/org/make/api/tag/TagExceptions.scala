package org.make.api.tag

object TagExceptions {
  final case class TagAlreadyExistsException(slug: String) extends Exception(s"Tag $slug already exists")
}
