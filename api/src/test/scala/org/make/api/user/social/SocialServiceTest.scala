package org.make.api.user.social

import org.scalatest.{FeatureSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

class SocialServiceTest
    extends FeatureSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with SocialServiceComponent {

  override val socialService: SocialService = new SocialService

  feature("login user from google provider") {
    scenario("successful login social user") {}
    scenario("social user has a bad token") {}
  }

  feature("login user from facebook provider") {
    scenario("successful login social user") {}
    scenario("social user has a bad token") {}
  }

  feature("login user from unknown provider") {}
}
