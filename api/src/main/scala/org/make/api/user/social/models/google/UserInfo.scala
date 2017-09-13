package org.make.api.user.social.models.google

import io.circe.Decoder

final case class UserInfo(azp: Option[String],
                          aud: Option[String],
                          sub: Option[String],
                          hd: Option[String],
                          email: String,
                          emailVerified: String,
                          atHash: Option[String],
                          iss: Option[String],
                          iat: Option[String],
                          exp: Option[String],
                          name: String,
                          picture: String,
                          givenName: String,
                          familyName: String,
                          local: Option[String],
                          alg: Option[String],
                          kid: Option[String])

object UserInfo {

  val MODERATOR_DOMAIN = "make.org"

  implicit val decoder: Decoder[UserInfo] =
    Decoder.forProduct17(
      "azp",
      "aud",
      "sub",
      "hd",
      "email",
      "email_verified",
      "at_hash",
      "iss",
      "iat",
      "exp",
      "name",
      "picture",
      "given_name",
      "family_name",
      "locale",
      "alg",
      "kid"
    )(UserInfo.apply)
}
