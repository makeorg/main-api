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

package org.make.api.technical.webflow
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.generic.semiauto.deriveDecoder
import org.make.api.MakeUnitTest

class DeserTest extends MakeUnitTest {

  Feature("webflow item") {
    val sampleItem =
      """{
                       |  "_archived": false,
                       |  "_draft": false,
                       |  "color": "#4CAF50",
                       |  "featured": false,
                       |  "name": "5 Principles Of Effective Web Design",
                       |  "post-body": "<h2>Earum laboriosam ab velit.</h2>",
                       |  "post-summary": "Quo eligendi nihil quia voluptas qui.\nNon distinctio voluptatu",
                       |  "thumbnail-image": {
                       |    "fileId": "580e63ff8c9a982ac9b8b74d",
                       |    "url": "https://d1otoma47x30pg.cloudfront.net/580e63fc8c9a982ac9b8b744/580e63ff8c9a982ac9b8b74d_1477338111010-image3.jpg"
                       |  },
                       |  "main-image": {
                       |    "fileId": "580e63fe8c9a982ac9b8b749",
                       |    "url": "https://d1otoma47x30pg.cloudfront.net/580e63fc8c9a982ac9b8b744/580e63fe8c9a982ac9b8b749_1477338110257-image20.jpg"
                       |  },
                       |  "slug": "5-principles-of-effective-web-design",
                       |  "updated-on": "2016-10-24T19:42:46.957Z",
                       |  "updated-by": "Person_5660c5338e9d3b0bee3b86aa",
                       |  "created-on": "2016-10-24T19:41:52.325Z",
                       |  "created-by": "Person_5660c5338e9d3b0bee3b86aa",
                       |  "published-on": "2016-10-24T19:43:15.745Z",
                       |  "published-by": "Person_5660c5338e9d3b0bee3b86aa",
                       |  "author": "580e640c8c9a982ac9b8b77a",
                       |  "_cid": "580e63fc8c9a982ac9b8b745",
                       |  "_id": "580e64008c9a982ac9b8b754"
                       |}""".stripMargin
    Scenario("simple item") {
      case class SimpleItem(postBody: String, postSummary: String)
      object SimpleItem {
        implicit val decoder: Decoder[SimpleItem] =
          Decoder.forProduct2("post-body", "post-summary")(SimpleItem.apply)
      }

      val decoded = decode[WebflowItem[SimpleItem]](sampleItem)
      decoded.isRight shouldBe true
      decoded.map(_.item).toOption should contain(
        SimpleItem(
          "<h2>Earum laboriosam ab velit.</h2>",
          "Quo eligendi nihil quia voluptas qui.\nNon distinctio voluptatu"
        )
      )
      decoded.map(_.metas.slug).toOption should contain("5-principles-of-effective-web-design")
    }
    Scenario("item has duplicates with metas") {
      case class DuplicateItem(name: String, postSummary: String)
      object DuplicateItem {
        implicit val decoder: Decoder[DuplicateItem] =
          Decoder.forProduct2("name", "post-summary")(DuplicateItem.apply)
      }

      val decoded = decode[WebflowItem[DuplicateItem]](sampleItem)
      decoded.isRight shouldBe true
      decoded.map(_.item).toOption should contain(
        DuplicateItem(
          "5 Principles Of Effective Web Design",
          "Quo eligendi nihil quia voluptas qui.\nNon distinctio voluptatu"
        )
      )
      decoded.map(_.metas.slug).toOption should contain("5-principles-of-effective-web-design")
    }
    Scenario("empty item") {
      case class EmptyItem()
      object EmptyItem {
        implicit val decoder: Decoder[EmptyItem] = deriveDecoder[EmptyItem]
      }

      val decoded = decode[WebflowItem[EmptyItem]](sampleItem)
      decoded.isRight shouldBe true
      decoded.map(_.item).toOption should contain(EmptyItem())
      decoded.map(_.metas.slug).toOption should contain("5-principles-of-effective-web-design")
    }
  }

  Feature("webflow items") {
    val sampleWebflowItems =
      """{
    |  "items": [
    |  {
    |    "_archived": false,
    |    "_draft": false,
    |    "color": "#4CAF50",
    |    "featured": false,
    |    "name": "5 Principles Of Effective Web Design",
    |    "post-body": "<h2>Earum laboriosam ab velit.</h2>",
    |    "post-summary": "Quo eligendi nihil quia voluptas qui.\nNon distinctio voluptatu",
    |    "thumbnail-image": {
    |      "fileId": "580e63ff8c9a982ac9b8b74d",
    |      "url": "https://d1otoma47x30pg.cloudfront.net/580e63fc8c9a982ac9b8b744/580e63ff8c9a982ac9b8b74d_1477338111010-image3.jpg"
    |    },
    |    "main-image": {
    |      "fileId": "580e63fe8c9a982ac9b8b749",
    |      "url": "https://d1otoma47x30pg.cloudfront.net/580e63fc8c9a982ac9b8b744/580e63fe8c9a982ac9b8b749_1477338110257-image20.jpg"
    |    },
    |    "slug": "5-principles-of-effective-web-design",
    |    "updated-on": "2016-10-24T19:42:46.957Z",
    |    "updated-by": "Person_5660c5338e9d3b0bee3b86aa",
    |    "created-on": "2016-10-24T19:41:52.325Z",
    |    "created-by": "Person_5660c5338e9d3b0bee3b86aa",
    |    "published-on": "2016-10-24T19:43:15.745Z",
    |    "published-by": "Person_5660c5338e9d3b0bee3b86aa",
    |    "author": "580e640c8c9a982ac9b8b77a",
    |    "_cid": "580e63fc8c9a982ac9b8b745",
    |    "_id": "580e64008c9a982ac9b8b754"
    |  }
    |  ],
    |  "count": 1,
    |  "limit": 1,
    |  "offset": 0,
    |  "total": 5
    |}
  """.stripMargin
    Scenario("simple item") {
      case class SampleItem(postBody: String, postSummary: String)
      object SampleItem {
        implicit val decoder: Decoder[SampleItem] =
          Decoder.forProduct2("post-body", "post-summary")(SampleItem.apply)
      }

      val decoded = decode[WebflowItems[SampleItem]](sampleWebflowItems)
      decoded.isRight shouldBe true
      decoded.map(_.items.size).toOption should contain(1)
      decoded.map(_.total).toOption should contain(5)
      decoded.map(_.items.head.item).toOption should contain(
        SampleItem(
          "<h2>Earum laboriosam ab velit.</h2>",
          "Quo eligendi nihil quia voluptas qui.\nNon distinctio voluptatu"
        )
      )
      decoded.map(_.items.head.metas.slug).toOption should contain("5-principles-of-effective-web-design")
    }
  }
}
