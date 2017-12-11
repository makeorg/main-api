package org.make.api.user.social.models.facebook

import io.circe.Decoder

final case class FacebookUserPictureData(isSilouhette: Boolean, url: String)

object FacebookUserPictureData {
  implicit val decoder: Decoder[FacebookUserPictureData] =
    Decoder.forProduct2("is_silhouette", "url")(FacebookUserPictureData.apply)

}

final case class FacebookUserPicture(data: FacebookUserPictureData)

object FacebookUserPicture {
  implicit val decoder: Decoder[FacebookUserPicture] =
    Decoder.forProduct1("data")(FacebookUserPicture.apply)

}

final case class UserInfo(id: String,
                          email: Option[String],
                          firstName: Option[String],
                          lastName: Option[String],
                          picture: FacebookUserPicture)

object UserInfo {
  implicit val decoder: Decoder[UserInfo] =
    Decoder.forProduct5("id", "email", "first_name", "last_name", "picture")(UserInfo.apply)
}
