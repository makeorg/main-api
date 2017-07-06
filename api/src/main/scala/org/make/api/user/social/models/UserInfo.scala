package org.make.api.user.social.models

final case class UserInfo(email: String,
                          firstName: String,
                          lastName: String,
                          googleId: Option[String],
                          facebookId: Option[String])
