package org.make.api.technical.elasticsearch

import akka.NotUsed
import akka.stream.scaladsl.Flow
import scala.concurrent.duration.DurationDouble

trait IndexationStream {
  //TODO: Load these values from conf
  val parallelism = 5
  val singleAsync = 1

  def filterIsDefined[T]: Flow[Option[T], T, NotUsed] = Flow[Option[T]].filter(_.isDefined).map(_.get)
  def filterIsEmpty[T](item: T): Flow[Option[T], T, NotUsed] = Flow[Option[T]].filter(_.isEmpty).map(_ => item)
  def grouped[T]: Flow[T, Seq[T], NotUsed] = Flow[T].groupedWithin(100, 500.milliseconds)

}