package org.make.api.user.social.models.facebook

import io.circe.Decoder

final case class UserInfo(email: String)

object UserInfo {
  implicit val decoder: Decoder[UserInfo] =
    Decoder.forProduct1("email")(UserInfo.apply)
}
