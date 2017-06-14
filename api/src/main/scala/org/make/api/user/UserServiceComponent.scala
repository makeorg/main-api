package org.make.api.user

import java.time.LocalDate

import org.make.api.technical.IdGeneratorComponent
import org.make.core.user.{User, UserId}

import scala.concurrent.Future

trait UserServiceComponent { this: IdGeneratorComponent with PersistentUserServiceComponent =>

  def userService: UserService

  class UserService {

    def getUser(id: UserId): Future[Option[User]] = {
      persistentUserService.get(id)
    }

    def register(email: String,
                 dateOfBirth: LocalDate,
                 firstName: String,
                 lastName: String,
                 password: String): Future[User] = {

      persistentUserService.persist(
        User(
          userId = idGenerator.nextUserId(),
          dateOfBirth = dateOfBirth,
          email = email,
          firstName = firstName,
          lastName = lastName
        ),
        password
      )
    }

  }

}
