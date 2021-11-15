/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.make.core.AvroSerializers

import java.util.concurrent.Future

trait KafkaConsumerTest[T] extends MakeKafkaTest with AvroSerializers {

  def topic: String
  val producer: KafkaProducer[String, T]

  override def afterAll(): Unit = {
    producer.close()
    Thread.sleep(2000)
    super.afterAll()
  }

  def send(event: T): Future[RecordMetadata] = {
    producer.send(new ProducerRecord[String, T](topic, event))

  }

}
