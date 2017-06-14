package org.make.core.user

import java.time.LocalDate

sealed trait UserCommand {
  def userId: UserId
}

case class RegisterCommand(userId: UserId,
                           email: String,
                           dateOfBirth: LocalDate,
                           firstName: String,
                           lastName: String)
    extends UserCommand

case class UpdateProfileCommand(userId: UserId) extends UserCommand

case class GetUser(userId: UserId) extends UserCommand

case class KillUserShard(userId: UserId) extends UserCommand
