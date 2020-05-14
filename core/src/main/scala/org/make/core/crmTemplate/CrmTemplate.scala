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

package org.make.core.crmTemplate
import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.question.QuestionId
import spray.json.{JsString, JsValue, JsonFormat}

case class CrmTemplates(
  crmTemplatesId: CrmTemplatesId,
  questionId: Option[QuestionId],
  locale: Option[String],
  registration: TemplateId,
  welcome: TemplateId,
  proposalAccepted: TemplateId,
  proposalRefused: TemplateId,
  forgottenPassword: TemplateId,
  resendRegistration: TemplateId,
  proposalAcceptedOrganisation: TemplateId,
  proposalRefusedOrganisation: TemplateId,
  forgottenPasswordOrganisation: TemplateId,
  organisationEmailChangeConfirmation: TemplateId,
  registrationB2B: TemplateId
)
object CrmTemplates {
  object MonitoringCategory {
    val account = "account"
    val welcome = "welcome"
    val moderation = "moderation"
  }
}

case class CrmTemplatesId(value: String) extends StringValue

object CrmTemplatesId {
  implicit lazy val CrmTemplatesIdEncoder: Encoder[CrmTemplatesId] =
    (a: CrmTemplatesId) => Json.fromString(a.value)
  implicit lazy val CrmTemplatesIdDecoder: Decoder[CrmTemplatesId] =
    Decoder.decodeString.map(CrmTemplatesId(_))

  implicit val CrmTemplatesIdFormatter: JsonFormat[CrmTemplatesId] = new JsonFormat[CrmTemplatesId] {
    override def read(json: JsValue): CrmTemplatesId = json match {
      case JsString(s) => CrmTemplatesId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: CrmTemplatesId): JsValue = {
      JsString(obj.value)
    }
  }
}

case class TemplateId(value: String) extends StringValue

object TemplateId {
  implicit lazy val TemplateIdEncoder: Encoder[TemplateId] =
    (a: TemplateId) => Json.fromString(a.value)
  implicit lazy val TemplateIdDecoder: Decoder[TemplateId] =
    Decoder.decodeInt.map(id => TemplateId(id.toString))

  implicit val TemplateIdFormatter: JsonFormat[TemplateId] = new JsonFormat[TemplateId] {
    override def read(json: JsValue): TemplateId = json match {
      case JsString(s) => TemplateId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TemplateId): JsValue = {
      JsString(obj.value)
    }
  }
}
