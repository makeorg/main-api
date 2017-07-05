package org.make.core.auth

import java.time.ZonedDateTime

import org.make.core.Timestamped
import org.make.core.user.User

case class Token(accessToken: String,
                 refreshToken: Option[String],
                 scope: Option[String],
                 expiresIn: Int,
                 user: User,
                 client: Client,
                 override val createdAt: Option[ZonedDateTime] = None,
                 override val updatedAt: Option[ZonedDateTime] = None)
    extends Timestamped
