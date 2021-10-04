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

package org.make.api.technical.crm

import java.nio.file.{Path, Paths}
import akka.Done
import enumeratum.values.{StringEnum, StringEnumEntry}
import io.circe.Decoder
import org.make.api.technical.crm.ManageContactAction.{AddNoForce, Remove}
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.core.Order

import scala.concurrent.Future

trait CrmService {
  def sendEmail(message: SendEmail): Future[SendEmailResponse]
  def synchronizeList(formattedDate: String, list: CrmList, csvDirectory: Path): Future[Done]
  def createCrmUsers(): Future[Unit]
  def anonymize(): Future[Unit]
  def synchronizeContactsWithCrm(): Future[JobAcceptance]
  def getUsersMailFromList(
    listId: Option[String] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    countOnly: Option[Boolean] = None,
    limit: Int,
    offset: Int = 0
  ): Future[GetUsersMail]
}

trait CrmServiceComponent {
  def crmService: CrmService
}

final case class GetUsersMail(count: Int, total: Int, data: Seq[ContactMail])
object GetUsersMail {
  implicit val decoder: Decoder[GetUsersMail] = Decoder.forProduct3("Count", "Total", "Data")(GetUsersMail.apply)
}

final case class ContactMail(email: String, contactId: Long)
object ContactMail {
  implicit val decoder: Decoder[ContactMail] = Decoder.forProduct2("Email", "ID")(ContactMail.apply)
}

sealed abstract class CrmList(val value: String) extends StringEnumEntry {
  def hardBounced: Boolean
  def unsubscribed: Option[Boolean]

  def targetDirectory(csvDirectory: String): Path = {
    Paths.get(csvDirectory, value)
  }

  def actionOnHardBounce: ManageContactAction
  def actionOnOptIn: ManageContactAction
  def actionOnOptOut: ManageContactAction
}

object CrmList extends StringEnum[CrmList] {
  case object HardBounce extends CrmList("hardBounce") {
    override val hardBounced: Boolean = true
    override val unsubscribed: Option[Boolean] = None

    override val actionOnHardBounce: ManageContactAction = AddNoForce
    override val actionOnOptIn: ManageContactAction = Remove
    override val actionOnOptOut: ManageContactAction = Remove
  }

  case object OptIn extends CrmList("optIn") {
    override val hardBounced: Boolean = false
    override val unsubscribed: Option[Boolean] = Some(false)

    override val actionOnHardBounce: ManageContactAction = Remove
    override val actionOnOptIn: ManageContactAction = AddNoForce
    override val actionOnOptOut: ManageContactAction = Remove
  }

  case object OptOut extends CrmList("optOut") {
    override val hardBounced: Boolean = false
    override val unsubscribed: Option[Boolean] = Some(true)

    override val actionOnHardBounce: ManageContactAction = Remove
    override val actionOnOptIn: ManageContactAction = Remove
    override val actionOnOptOut: ManageContactAction = AddNoForce
  }

  override def values: IndexedSeq[CrmList] = findValues
}
