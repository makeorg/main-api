package org.make.api.technical.auth

import scala.concurrent.Future

trait OauthTokenGenerator {
  def generateAccessToken(): Future[(String, String)]
  def generateRefreshToken(): Future[(String, String)]
}

trait OauthTokenGeneratorComponent {
  def oauthTokenGenerator: OauthTokenGenerator
}

trait DefaultOauthTokenGeneratorComponent extends OauthTokenGeneratorComponent {
  this: TokenGeneratorComponent with PersistentTokenServiceComponent =>

  override lazy val oauthTokenGenerator = new OauthTokenGenerator {
    override def generateRefreshToken(): Future[(String, String)] = {
      tokenGenerator.generateToken(persistentTokenService.refreshTokenExists)
    }

    override def generateAccessToken(): Future[(String, String)] = {
      tokenGenerator.generateToken(persistentTokenService.accessTokenExists)
    }
  }
}
