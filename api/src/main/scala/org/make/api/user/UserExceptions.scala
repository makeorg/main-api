package org.make.api.user

/**
  * Created by amine on 30/06/2017.
  */
object UserExceptions {
  final case class EmailAlreadyRegisteredException(email: String) extends Exception(s"Email $email already exist")
}
