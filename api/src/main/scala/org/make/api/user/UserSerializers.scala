package org.make.api.user

import org.make.api.technical.SprayJsonFormatters
import org.make.core.user.User
import org.make.core.user.UserEvent.{UserRegistered, UserViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object UserSerializers extends SprayJsonFormatters {

  implicit private val userRegisteredFormatter: RootJsonFormat[UserRegistered] =
    DefaultJsonProtocol.jsonFormat5(UserRegistered)

  implicit private val userViewedFormatter: RootJsonFormat[UserViewed] =
    DefaultJsonProtocol.jsonFormat1(UserViewed)

  implicit private val userFormatter: RootJsonFormat[User] =
    DefaultJsonProtocol.jsonFormat5(User)

  private val userRegisteredSerializer: JsonPersister[UserRegistered, V1] =
    persister[UserRegistered]("user-registered")

  private val userViewedSerializer: JsonPersister[UserViewed, V1] =
    persister[UserViewed]("user-viewed")

  private val userSerializer: JsonPersister[User, V1] =
    persister[User]("user")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(userRegisteredSerializer, userViewedSerializer, userSerializer)
}
