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

package org.make.api.technical
import java.io.File
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType, IllegalUriException}
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class DownloadServiceTest extends MakeUnitTest with DefaultDownloadServiceComponent with ActorSystemComponent {
  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  Feature("download image") {
    def destFn(contentType: ContentType): File =
      Files.createTempFile("tmp", s".${contentType.mediaType.subType}").toFile

    Scenario("correct image url") {
      val imageUrl = "https://assets.make.org/assets/home/hero-desktop-170720.png?w=680"
      val futureImage = downloadService.downloadImage(imageUrl, destFn)
      whenReady(futureImage, Timeout(3.seconds)) {
        case (contentType, _) =>
          contentType.mediaType.isImage shouldBe true
      }
    }

    Scenario("not an image") {
      val imageUrl = "https://google.com"
      val futureImage = downloadService.downloadImage(imageUrl, destFn)

      whenReady(futureImage.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[IllegalStateException]
        exception
          .asInstanceOf[IllegalStateException]
          .getMessage
          .contains("URL does not refer to an image") shouldBe true
      }

    }

    Scenario("URL not found") {
      val imageUrl = "https://api.make.org/404"
      val futureImage = downloadService.downloadImage(imageUrl, destFn)

      whenReady(futureImage.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[ImageUnavailable]
        exception
          .asInstanceOf[ImageUnavailable]
          .getMessage
          .startsWith("Image not found for URL") shouldBe true
      }

    }

    Scenario("empty URL") {
      val imageUrl = ""
      val futureImage = downloadService.downloadImage(imageUrl, destFn)

      whenReady(futureImage.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[IllegalUriException]
      }
    }
  }
}
