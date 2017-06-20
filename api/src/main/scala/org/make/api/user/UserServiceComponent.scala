package org.make.api.user

import org.make.api.technical.IdGeneratorComponent
import org.make.core.user.{User, UserId}

import scala.concurrent.Future

trait UserServiceComponent { this: IdGeneratorComponent with PersistentUserServiceComponent =>

  def userService: UserService

  class UserService {

    def getUser(id: UserId): Future[Option[User]] = {
      persistentUserService.get(id)
    }
  }

}
