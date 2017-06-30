package org.make.api

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.FlatSpec

// toDo: this trait must be shared between test and it
trait MakeTest
    extends FlatSpec
    with GivenWhenThen
    with MockitoSugar
    with Matchers
    with StrictLogging
    with BeforeAndAfterAll
    with ScalaFutures {}
