package org.make.api.idea

object IdeaExceptions {
  final case class IdeaAlreadyExistsException(name: String) extends Exception(s"Idea $name already exists")
}
