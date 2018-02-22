package org.make.api.user.social.models

final case class UserInfo(email: Option[String] = None,
                          firstName: Option[String],
                          lastName: Option[String],
                          country: String,
                          language: String,
                          gender: Option[String] = None,
                          googleId: Option[String] = None,
                          facebookId: Option[String] = None,
                          picture: Option[String] = None,
                          domain: Option[String] = None)
