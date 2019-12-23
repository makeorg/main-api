package org.make.api.technical
import java.io.File
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentType
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scala.concurrent.duration.DurationInt

class DownloadServiceTest extends MakeUnitTest with DefaultDownloadServiceComponent with ActorSystemComponent {
  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)

  feature("download image") {
    def destFn(contentType: ContentType): File =
      Files.createTempFile("tmp", s".${contentType.mediaType.subType}").toFile

    scenario("correct image url") {
      val imageUrl = "https://via.placeholder.com/150"
      val futureImage = downloadService.downloadImage(imageUrl, destFn)
      whenReady(futureImage, Timeout(3.seconds)) {
        case (contentType, _) =>
          contentType.mediaType.isImage shouldBe true
      }
    }

    scenario("not an image") {
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

    scenario("failed URL") {
      val imageUrl = "https://api.make.org/404"
      val futureImage = downloadService.downloadImage(imageUrl, destFn)

      whenReady(futureImage.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[IllegalStateException]
        exception
          .asInstanceOf[IllegalStateException]
          .getMessage
          .contains("URL failed with status code: 404") shouldBe true
      }

    }
  }
}
