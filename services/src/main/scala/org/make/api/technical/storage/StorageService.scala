/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
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

package org.make.api.technical.storage

import java.io.{ByteArrayOutputStream, File, FileInputStream, InputStream}
import java.net.URL

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.ApiModelProperty
import org.make.api.ConfigComponent
import org.make.core.user.{UserId, UserType}

import scala.annotation.meta.field
import scala.concurrent.Future

trait StorageService {

  def uploadFile(fileType: FileType, name: String, contentType: String, content: Content): Future[String]
  def uploadUserAvatar(userId: UserId, name: String, contentType: String, content: Content): Future[String]
  def uploadAdminUserAvatar(
    extension: String,
    contentType: String,
    content: Content,
    userType: UserType
  ): Future[String]
}

final case class UploadResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "https://example.com/path/to/image.png")
  path: String
)

object UploadResponse {
  implicit val encoder: Encoder[UploadResponse] = deriveEncoder[UploadResponse]
  implicit val decoder: Decoder[UploadResponse] = deriveDecoder[UploadResponse]
}

sealed trait FileType {
  def name: String
  def path: String
}

object FileType {

  // Use this file type for logos and avatars
  case object Avatar extends FileType {
    override def name: String = "Avatar"
    override def path: String = "avatars"
  }

  // Use this file type for the images related to operations, on home page or operation page
  case object Operation extends FileType {
    override def name: String = "Operation"
    override def path: String = "content/operations"
  }

  case object Home extends FileType {
    override def name: String = "Home"
    override def path: String = "content/home"
  }
}

trait Content {
  def toByteArray(): Array[Byte]
}

object Content {
  @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
  final case class ByteArrayContent(content: Array[Byte]) extends Content {
    override def toByteArray(): Array[Byte] = content
  }

  // *Blocking* implementation of reading files
  final case class InputStreamContent(content: InputStream) extends Content {
    @SuppressWarnings(Array("org.wartremover.warts.While"))
    override def toByteArray(): Array[Byte] = {
      val bufferSize = 2048
      val buffer = Array.ofDim[Byte](bufferSize)
      val output = new ByteArrayOutputStream()
      while ({
        val readBytes = content.read(buffer)
        if (readBytes != -1) {
          output.write(buffer, 0, readBytes)
        }
        readBytes != -1
      }) {}

      output.toByteArray
    }
  }

  final case class FileContent(content: File) extends Content {
    override def toByteArray(): Array[Byte] = InputStreamContent(new FileInputStream(content)).toByteArray()
  }

  // *Blocking* implementation of retrieving files from URL
  final case class UrlContent(content: URL) extends Content {
    override def toByteArray(): Array[Byte] = {
      InputStreamContent(content.openStream()).toByteArray()
    }
  }

}

trait StorageServiceComponent {
  def storageService: StorageService
}

final case class StorageConfiguration(bucketName: String, baseUrl: String, maxFileSize: Long)

trait StorageConfigurationComponent {
  def storageConfiguration: StorageConfiguration
}

trait DefaultStorageConfigurationComponent extends StorageConfigurationComponent {
  self: ConfigComponent =>

  override lazy val storageConfiguration: StorageConfiguration = {
    val configuration = config.getConfig("make-api.storage")
    StorageConfiguration(
      bucketName = configuration.getString("bucket-name"),
      baseUrl = configuration.getString("base-url"),
      maxFileSize = configuration.getLong("max-upload-file-size")
    )
  }
}
