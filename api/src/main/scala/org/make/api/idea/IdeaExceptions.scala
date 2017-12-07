package org.make.api.idea

object IdeaExceptions {
  final case class IdeaAlreadyExistsException(name: String) extends Exception(s"Idea $name already exists")
  final case class IdeaDoesnotExistsException(uuid: String) extends Exception(s"Idea with uuid $uuid does not exist")
}
