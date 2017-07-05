package org.make.core.auth

import java.time.ZonedDateTime

import org.make.core.{StringValue, Timestamped}

case class Client(clientId: ClientId,
                  allowedGrantTypes: Seq[String],
                  secret: Option[String],
                  scope: Option[String],
                  redirectUri: Option[String],
                  override val createdAt: Option[ZonedDateTime] = None,
                  override val updatedAt: Option[ZonedDateTime] = None)
    extends Timestamped

case class ClientId(value: String) extends StringValue
