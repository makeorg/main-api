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

import kamon.annotation.api.Trace
import org.make.api.technical.IdGeneratorComponent
import org.make.core.DateHelper
import org.make.core.user.UserType
import org.make.swift.model.Bucket

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DefaultStorageServiceComponent extends StorageServiceComponent {
  self: SwiftClientComponent with StorageConfigurationComponent with IdGeneratorComponent =>

  override lazy val storageService: StorageService = new DefaultStorageService

  class DefaultStorageService extends StorageService {

    @Trace(operationName = "client-uploadFile")
    override def uploadFile(fileType: FileType, name: String, contentType: String, content: Content): Future[String] = {
      val path = s"${fileType.path}/$name"
      swiftClient
        .sendFile(Bucket(0, 0, storageConfiguration.bucketName), path, contentType, content.toByteArray())
        .map { _ =>
          s"${storageConfiguration.baseUrl}/$path"
        }
    }

    @Trace(operationName = "client-uploadUserAvatar")
    override def uploadUserAvatar(extension: String, contentType: String, content: Content): Future[String] = {
      val date = DateHelper.now()
      val name = s"${date.getYear}/${date.getMonthValue}/${idGenerator.nextId()}.$extension"
      storageService.uploadFile(FileType.Avatar, name, contentType, content)

    }

    @Trace(operationName = "client-uploadAdminUserAvatar")
    override def uploadAdminUserAvatar(
      extension: String,
      contentType: String,
      content: Content,
      userType: UserType
    ): Future[String] = {
      val date = DateHelper.now()
      val name =
        s"${date.getYear}/${date.getMonthValue}/${userType.value.toLowerCase}/${idGenerator.nextId()}.$extension"
      storageService.uploadFile(FileType.Avatar, name, contentType, content)

    }
  }
}
