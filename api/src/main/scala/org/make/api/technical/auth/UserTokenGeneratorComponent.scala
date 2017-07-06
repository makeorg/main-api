package org.make.api.technical.auth

import org.make.api.user.PersistentUserServiceComponent

import scala.concurrent.Future

trait UserTokenGenerator {
  def generateVerificationToken(): Future[(String, String)]
  def generateResetToken(): Future[(String, String)]
}

trait UserTokenGeneratorComponent {
  def userTokenGenerator: UserTokenGenerator
}

trait DefaultUserTokenGeneratorComponent extends UserTokenGeneratorComponent {
  this: TokenGeneratorComponent with PersistentUserServiceComponent =>

  override lazy val userTokenGenerator: UserTokenGenerator = new UserTokenGenerator {
    override def generateVerificationToken(): Future[(String, String)] = {
      tokenGenerator.generateToken(persistentUserService.verificationTokenExists)
    }
    override def generateResetToken(): Future[(String, String)] = {
      tokenGenerator.generateToken(persistentUserService.resetTokenExists)
    }
  }
}
