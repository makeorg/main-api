package org.make.api.user.social.models

final case class UserInfo(email: String,
                          firstName: String,
                          lastName: String,
                          googleId: Option[String] = None,
                          facebookId: Option[String] = None,
                          picture: Option[String] = None,
                          domain: Option[String] = None)
