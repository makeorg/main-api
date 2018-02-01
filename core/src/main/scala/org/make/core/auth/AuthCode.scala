package org.make.core.auth

import java.time.ZonedDateTime

import org.make.core.user.User

case class AuthCode(authorizationCode: String,
                    scope: Option[String],
                    redirectUri: Option[String],
                    createdAt: ZonedDateTime,
                    expiresIn: Int,
                    user: User,
                    client: Client)
