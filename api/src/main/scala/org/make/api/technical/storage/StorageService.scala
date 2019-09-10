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

import org.make.api.ActorSystemComponent
import org.make.swift.model.Bucket

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait StorageService {

  def uploadFile(fileType: FileType, name: String, contentType: String, content: Content): Future[String]

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
}

trait Content {
  def toByteArray(): Array[Byte]
}

object Content {
  case class ByteArrayContent(content: Array[Byte]) extends Content {
    override def toByteArray(): Array[Byte] = content
  }

  // *Blocking* implementation of reading files
  case class InputStreamContent(content: InputStream) extends Content {
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

  case class FileContent(content: File) extends Content {
    override def toByteArray(): Array[Byte] = InputStreamContent(new FileInputStream(content)).toByteArray()
  }

  // *Blocking* implementation of retrieving files from URL
  case class UrlContent(content: URL) extends Content {
    override def toByteArray(): Array[Byte] = {
      InputStreamContent(content.openStream()).toByteArray()
    }
  }

}

trait StorageServiceComponent {
  def storageService: StorageService
}

trait DefaultStorageServiceComponent extends StorageServiceComponent {
  self: SwiftClientComponent with StorageConfigurationComponent =>

  override lazy val storageService: StorageService = new DefaultStorageService

  class DefaultStorageService extends StorageService {
    override def uploadFile(fileType: FileType, name: String, contentType: String, content: Content): Future[String] = {
      val path = s"${fileType.path}/$name"
      swiftClient
        .sendFile(Bucket(0, 0, storageConfiguration.bucketName), path, contentType, content.toByteArray())
        .map { _ =>
          s"${storageConfiguration.baseUrl}/$path"
        }
    }
  }

}

case class StorageConfiguration(bucketName: String, baseUrl: String)

trait StorageConfigurationComponent {
  def storageConfiguration: StorageConfiguration
}

trait DefaultStorageConfigurationComponent extends StorageConfigurationComponent {
  self: ActorSystemComponent =>

  override lazy val storageConfiguration: StorageConfiguration = {
    val configuration = actorSystem.settings.config.getConfig("make-api.storage")
    StorageConfiguration(
      bucketName = configuration.getString("bucket-name"),
      baseUrl = configuration.getString("base-url")
    )
  }
}
