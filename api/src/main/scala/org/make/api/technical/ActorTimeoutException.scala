package org.make.api.technical

final case class ActorTimeoutException(entity: Any, cause: Throwable)
    extends Exception(s"A timeout occurred while sending message ${entity.toString}", cause)
