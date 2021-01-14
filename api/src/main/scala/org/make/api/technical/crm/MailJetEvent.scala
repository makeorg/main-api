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

import java.time.ZonedDateTime

import com.sksamuel.avro4s
import com.sksamuel.avro4s._
import grizzled.slf4j.Logging
import io.circe.Decoder._
import io.circe.{Decoder, DecodingFailure, HCursor}
import org.apache.avro.Schema
import org.make.core.{AvroSerializers, CirceFormatters, Sharded}

sealed trait MailJetEvent extends Product with Serializable {
  def email: String
  def time: Option[Long]
  def messageId: Option[Long]
  def campaignId: Option[Long]
  def contactId: Option[Long]
  def customCampaign: Option[String]
  def customId: Option[String]
  def payload: Option[String]
}

/**
  * see Mailjet documentation: https://dev.mailjet.com/guides/
  */
object MailJetEvent {
  private val eventDecoderMap = Map(
    "simple" -> MailJetBaseEvent.decoder,
    "bounce" -> MailJetBounceEvent.decoder,
    "blocked" -> MailJetBlockedEvent.decoder,
    "spam" -> MailJetSpamEvent.decoder,
    "unsub" -> MailJetUnsubscribeEvent.decoder
  )

  implicit val decoder: Decoder[MailJetEvent] = (cursor: HCursor) => {
    cursor.downField("event").as[String].flatMap { event =>
      eventDecoderMap
        .get(event)
        .orElse(eventDecoderMap.get("simple"))
        .map { decoder =>
          decoder.apply(cursor)
        }
        .getOrElse(Left(DecodingFailure(s"Unknown event type: $event", Nil)))
    }
  }
}

final case class MailJetEventWrapper(version: Int, id: String, date: ZonedDateTime, event: MailJetEvent) extends Sharded

object MailJetEventWrapper extends AvroSerializers {
  lazy val schemaFor: SchemaFor[MailJetEventWrapper] = SchemaFor.gen[MailJetEventWrapper]
  implicit lazy val avroDecoder: avro4s.Decoder[MailJetEventWrapper] = avro4s.Decoder.gen[MailJetEventWrapper]
  implicit lazy val avroEncoder: avro4s.Encoder[MailJetEventWrapper] = avro4s.Encoder.gen[MailJetEventWrapper]
  lazy val recordFormat: RecordFormat[MailJetEventWrapper] =
    RecordFormat[MailJetEventWrapper](schemaFor.schema(DefaultFieldMapper))
}

sealed trait MailJetErrorRelatedTo {
  def name: String
}
object MailJetErrorRelatedTo {
  case object Recipient extends MailJetErrorRelatedTo {
    override val name = "recipient"
  }
  case object Domain extends MailJetErrorRelatedTo {
    override val name = "domain"
  }
  case object Content extends MailJetErrorRelatedTo {
    override val name = "content"
  }
  case object Spam extends MailJetErrorRelatedTo {
    override val name = "spam"
  }
  case object System extends MailJetErrorRelatedTo {
    override val name = "system"
  }
  case object Mailjet extends MailJetErrorRelatedTo {
    override val name = "mailjet"
  }
}

sealed trait MailJetError extends Product with Serializable {
  def relatedTo: MailJetErrorRelatedTo
  def name: String
}
object MailJetError extends Logging {

  implicit object MailJetErrorAvroEncoder extends Encoder[MailJetError] {
    override def encode(value: MailJetError, schema: Schema, fieldMapper: FieldMapper): String = value.name
  }

  implicit object MailJetErrorFromValue extends avro4s.Decoder[MailJetError] {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): MailJetError =
      MailJetError.errorMap
        .getOrElse(value.toString, throw new IllegalArgumentException(s"$value is not a MailJetError"))
  }

  implicit object MailJetErrorToSchema extends SchemaFor[MailJetError] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  val errorMap: Map[String, MailJetError] = Map(
    UserUnknown.name -> UserUnknown,
    MailboxInactive.name -> MailboxInactive,
    QuotaExceeded.name -> QuotaExceeded,
    Blacklisted.name -> Blacklisted,
    SpamReporter.name -> SpamReporter,
    InvalidDomaine.name -> InvalidDomaine,
    NoMailHost.name -> NoMailHost,
    RelayAccessDenied.name -> RelayAccessDenied,
    Greylisted.name -> Greylisted,
    Typofix.name -> Typofix,
    BadOrEmptyTemplate.name -> BadOrEmptyTemplate,
    ErrorInTemplateLanguage.name -> ErrorInTemplateLanguage,
    SenderBlocked.name -> SenderBlocked,
    ContentBlocked.name -> ContentBlocked,
    PolicyIssue.name -> PolicyIssue,
    SystemIssue.name -> SystemIssue,
    ProtocolIssue.name -> ProtocolIssue,
    ConnectionIssue.name -> ConnectionIssue,
    Preblocked.name -> Preblocked,
    DuplicateInCampaign.name -> DuplicateInCampaign
  )

  def getError(maybeError: Option[String], maybeErrorRelatedTo: Option[String]): Option[MailJetError] = {
    maybeError.flatMap { error =>
      maybeErrorRelatedTo.flatMap { errorRelatedTo =>
        errorMap.get(error) match {
          case Some(mailJetError) if mailJetError.relatedTo.name == errorRelatedTo => Some(mailJetError)
          case None if maybeError.contains("") && maybeErrorRelatedTo.contains("") => None
          case _ =>
            logger.warn(s"""MailJet error not defined (error: "$error", errorRelated: "$errorRelatedTo").""")
            None
        }
      }
    }
  }

  case object UserUnknown extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Recipient
    override def name: String = "user unknown"
  }
  case object MailboxInactive extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Recipient
    override def name: String = "mailbox inactive"
  }
  case object QuotaExceeded extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Recipient
    override def name: String = "quota exceeded"
  }
  case object Blacklisted extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Recipient
    override def name: String = "blacklisted"
  }
  case object SpamReporter extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Recipient
    override def name: String = "spam reporter"
  }
  case object InvalidDomaine extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Domain
    override def name: String = "invalid domain"
  }
  case object NoMailHost extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Domain
    override def name: String = "no mail host"
  }
  case object RelayAccessDenied extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Domain
    override def name: String = "relay/access denied"
  }
  case object Greylisted extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Domain
    override def name: String = "greylisted"
  }
  case object Typofix extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Domain
    override def name: String = "typofix"
  }
  case object BadOrEmptyTemplate extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Content
    override def name: String = "bad or empty template"
  }
  case object ErrorInTemplateLanguage extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Content
    override def name: String = "error in template language"
  }
  case object SenderBlocked extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Spam
    override def name: String = "sender blocked"
  }
  case object ContentBlocked extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Spam
    override def name: String = "content blocked"
  }
  case object PolicyIssue extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Spam
    override def name: String = "policy issue"
  }
  case object SystemIssue extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.System
    override def name: String = "system issue"
  }
  case object ProtocolIssue extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.System
    override def name: String = "protocol issue"
  }
  case object ConnectionIssue extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.System
    override def name: String = "connection issue"
  }
  case object Preblocked extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Mailjet
    override def name: String = "preblocked"
  }
  case object DuplicateInCampaign extends MailJetError {
    override def relatedTo: MailJetErrorRelatedTo = MailJetErrorRelatedTo.Mailjet
    override def name: String = "duplicate in campaign"
  }
}

@AvroSortPriority(5)
final case class MailJetBaseEvent(
  event: String,
  override val email: String,
  override val time: Option[Long],
  override val messageId: Option[Long],
  override val campaignId: Option[Long],
  override val contactId: Option[Long],
  override val customCampaign: Option[String],
  override val customId: Option[String],
  override val payload: Option[String]
) extends MailJetEvent
object MailJetBaseEvent extends CirceFormatters {
  val decoder: Decoder[MailJetBaseEvent] = Decoder.forProduct9(
    "event",
    "email",
    "time",
    "MessageID",
    "mj_campaign_id",
    "mj_contact_id",
    "customcampaign",
    "CustomID",
    "Payload"
  ) {
    (
      event: String,
      email: String,
      time: Option[Long],
      messageId: Option[Long],
      campaignId: Either[Option[Long], String],
      contactId: Either[Option[Long], String],
      customCampaign: Option[String],
      customId: Option[String],
      payload: Option[String]
    ) =>
      MailJetBaseEvent(
        event = event,
        email = email,
        time = time,
        messageId = messageId,
        customCampaign = customCampaign,
        campaignId = campaignId match {
          case Left(value) => value
          case Right(_)    => None
        },
        contactId = contactId match {
          case Left(value) => value
          case Right(_)    => None
        },
        customId = customId,
        payload = payload
      )
  }
}

@AvroSortPriority(2)
final case class MailJetBounceEvent(
  override val email: String,
  override val time: Option[Long] = None,
  override val messageId: Option[Long] = None,
  override val campaignId: Option[Long] = None,
  override val contactId: Option[Long] = None,
  override val customCampaign: Option[String] = None,
  override val customId: Option[String] = None,
  override val payload: Option[String] = None,
  blocked: Boolean,
  hardBounce: Boolean,
  error: Option[MailJetError]
) extends MailJetEvent

object MailJetBounceEvent extends CirceFormatters {
  val decoder: Decoder[MailJetBounceEvent] = Decoder.forProduct12(
    "email",
    "time",
    "MessageID",
    "mj_campaign_id",
    "mj_contact_id",
    "customcampaign",
    "CustomID",
    "Payload",
    "blocked",
    "hard_bounce",
    "error_related_to",
    "error"
  ) {
    (
      email: String,
      time: Option[Long],
      messageId: Option[Long],
      campaignId: Either[Option[Long], String],
      contactId: Either[Option[Long], String],
      customCampaign: Option[String],
      customId: Option[String],
      payload: Option[String],
      blocked: Either[Boolean, String],
      hardBounce: Either[Boolean, String],
      errorRelatedTo: Option[String],
      error: Option[String]
    ) =>
      MailJetBounceEvent(
        email = email,
        time = time,
        messageId = messageId,
        customCampaign = customCampaign,
        campaignId = campaignId match {
          case Left(value) => value
          case Right(_)    => None
        },
        contactId = contactId match {
          case Left(value) => value
          case Right(_)    => None
        },
        customId = customId,
        payload = payload,
        blocked = blocked match {
          case Left(value) => value
          case _           => false
        },
        hardBounce = hardBounce match {
          case Left(value) => value
          case _           => false
        },
        error = MailJetError.getError(error, errorRelatedTo)
      )

  }
}

@AvroSortPriority(1)
final case class MailJetBlockedEvent(
  override val email: String,
  override val time: Option[Long] = None,
  override val messageId: Option[Long] = None,
  override val campaignId: Option[Long] = None,
  override val contactId: Option[Long] = None,
  override val customCampaign: Option[String] = None,
  override val customId: Option[String] = None,
  override val payload: Option[String] = None,
  error: Option[MailJetError]
) extends MailJetEvent
object MailJetBlockedEvent extends CirceFormatters {
  val decoder: Decoder[MailJetBlockedEvent] = Decoder.forProduct10(
    "email",
    "time",
    "MessageID",
    "mj_campaign_id",
    "mj_contact_id",
    "customcampaign",
    "CustomID",
    "Payload",
    "error_related_to",
    "error"
  ) {
    (
      email: String,
      time: Option[Long],
      messageId: Option[Long],
      campaignId: Either[Option[Long], String],
      contactId: Either[Option[Long], String],
      customCampaign: Option[String],
      customId: Option[String],
      payload: Option[String],
      errorRelatedTo: Option[String],
      error: Option[String]
    ) =>
      MailJetBlockedEvent(
        email = email,
        time = time,
        messageId = messageId,
        customCampaign = customCampaign,
        campaignId = campaignId match {
          case Left(value) => value
          case Right(_)    => None
        },
        contactId = contactId match {
          case Left(value) => value
          case Right(_)    => None
        },
        customId = customId,
        payload = payload,
        error = MailJetError.getError(error, errorRelatedTo)
      )
  }
}

@AvroSortPriority(3)
final case class MailJetSpamEvent(
  override val email: String,
  override val time: Option[Long] = None,
  override val messageId: Option[Long] = None,
  override val campaignId: Option[Long] = None,
  override val contactId: Option[Long] = None,
  override val customCampaign: Option[String] = None,
  override val customId: Option[String] = None,
  override val payload: Option[String] = None,
  source: Option[String]
) extends MailJetEvent
object MailJetSpamEvent extends CirceFormatters {
  val decoder: Decoder[MailJetSpamEvent] = Decoder.forProduct9(
    "email",
    "time",
    "MessageID",
    "mj_campaign_id",
    "mj_contact_id",
    "customcampaign",
    "CustomID",
    "Payload",
    "source"
  ) {
    (
      email: String,
      time: Option[Long],
      messageId: Option[Long],
      campaignId: Either[Option[Long], String],
      contactId: Either[Option[Long], String],
      customCampaign: Option[String],
      customId: Option[String],
      payload: Option[String],
      source: Option[String]
    ) =>
      MailJetSpamEvent(email = email, time = time, messageId = messageId, campaignId = campaignId match {
        case Left(value) => value
        case Right(_)    => None
      }, contactId = contactId match {
        case Left(value) => value
        case Right(_)    => None
      }, customCampaign = customCampaign, customId = customId, payload = payload, source = source)
  }
}

@AvroSortPriority(4)
final case class MailJetUnsubscribeEvent(
  email: String,
  time: Option[Long] = None,
  messageId: Option[Long] = None,
  campaignId: Option[Long] = None,
  contactId: Option[Long] = None,
  customCampaign: Option[String] = None,
  customId: Option[String] = None,
  payload: Option[String] = None,
  listId: Option[Int],
  ip: Option[String],
  geo: Option[String],
  agent: Option[String]
) extends MailJetEvent
object MailJetUnsubscribeEvent extends CirceFormatters {
  val decoder: Decoder[MailJetUnsubscribeEvent] = Decoder.forProduct12(
    "email",
    "time",
    "MessageID",
    "mj_campaign_id",
    "mj_contact_id",
    "customcampaign",
    "CustomID",
    "Payload",
    "mj_list_id",
    "ip",
    "geo",
    "agent"
  ) {
    (
      email: String,
      time: Option[Long],
      messageId: Option[Long],
      campaignId: Either[Option[Long], String],
      contactId: Either[Option[Long], String],
      customCampaign: Option[String],
      customId: Option[String],
      payload: Option[String],
      listId: Either[Option[Int], String],
      ip: Option[String],
      geo: Option[String],
      agent: Option[String]
    ) =>
      MailJetUnsubscribeEvent(email = email, time = time, messageId = messageId, campaignId = campaignId match {
        case Left(value) => value
        case Right(_)    => None
      }, contactId = contactId match {
        case Left(value) => value
        case Right(_)    => None
      }, customCampaign = customCampaign, customId = customId, payload = payload, listId = listId match {
        case Left(value) => value
        case Right(_)    => None
      }, ip = ip, geo = geo, agent = agent)

  }
}
