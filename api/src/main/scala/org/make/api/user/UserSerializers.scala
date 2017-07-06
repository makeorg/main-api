package org.make.api.user

import org.make.api.technical.SprayJsonFormatters
import org.make.core.profile.{Gender, Profile}
import org.make.core.user.{Role, User}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import stamina.V1
import stamina.json._

object UserSerializers extends SprayJsonFormatters {

  implicit val genderFormatter: JsonFormat[Gender] = new JsonFormat[Gender] {
    override def read(json: JsValue): Gender = json match {
      case JsString(gender) =>
        Gender.matchGender(gender).getOrElse(throw new IllegalArgumentException(s"$gender is not a Gender"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(gender: Gender): JsValue = JsString(gender.shortName)
  }

  implicit private val profileFormatter: RootJsonFormat[Profile] =
    DefaultJsonProtocol.jsonFormat13(Profile.apply)

  implicit val roleFormatter: JsonFormat[Role] = new JsonFormat[Role] {
    override def read(json: JsValue): Role = json match {
      case JsString(role) => Role.matchRole(role).getOrElse(throw new IllegalArgumentException(s"$role is not a Role"))
      case other          => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(role: Role): JsValue = JsString(role.shortName)
  }

  implicit private val userFormatter: RootJsonFormat[User] =
    DefaultJsonProtocol.jsonFormat18(User)

  private val userSerializer: JsonPersister[User, V1] =
    persister[User]("user")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(userSerializer)
}
