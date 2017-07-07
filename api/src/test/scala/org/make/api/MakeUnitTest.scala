package org.make.api

import com.typesafe.scalalogging.StrictLogging
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

// toDo: this trait must be shared between test and it
trait MakeUnitTest extends FeatureSpec
  with GivenWhenThen
  with MockitoSugar
  with Matchers
  with StrictLogging
  with BeforeAndAfterAll
  with ScalaFutures
