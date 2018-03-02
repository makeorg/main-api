package org.make.api.serialization

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Base64

object CreateSerializationFromJson extends App {

  val json =
    """
      |{"author":{"userId":"my-user-id","firstName":"first name","postalCode":"75011","age":42},"slug":"my-proposal","id":"proposal-id","operation":"my-operation","eventDate":"2018-03-01T16:09:30.441Z","content":"my proposal","requestContext":{"requestId":"","sessionId":"","externalId":""},"userId":"my-user-id","theme":"theme-id"}
    """.stripMargin

  val data = Base64.getEncoder.encode(json.getBytes("UTF-8"))
  val destination =
    Paths.get("/home/francois/dev/make/make-api/api/src/test/resources/serialization/proposal-proposed-v1-default")

  Files.write(destination, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

}
