package org.make.api

import org.make.api.docker.DockerKafkaService

trait KafkaTest extends ItMakeTest with DockerKafkaService {
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }

}
