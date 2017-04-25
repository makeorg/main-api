package org.make.api.auth

import java.time.ZonedDateTime

trait TokenServiceComponent {

  def tokenService: TokenService

  class TokenService {



  }

  object TokenService {

    case class Token(
                    id: String,
                    refreshToken: String,
                    citizenId: String,
                    scope: String,
                    creationDate: ZonedDateTime,
                    validityDurationSeconds: Int,
                    paramters: String)


  }


}
