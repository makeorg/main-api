package org.make.api.technical.crm

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, DecodingFailure, HCursor}
import org.make.api.technical.crm.MailJetEvent.AnyMailJetEvent
import org.make.core.Sharded
import shapeless.{:+:, CNil, Coproduct, Poly1}

sealed trait MailJetEvent {
  def email: String
  def time: Option[Long]
  def messageId: Option[Long]
  def campaignId: Option[Int]
  def contactId: Option[Int]
  def customCampaign: Option[String]
  def customId: Option[String]
  def payload: Option[String]
}

/**
  * see Mailjet documentation: https://dev.mailjet.com/guides/
  */
object MailJetEvent {
  type AnyMailJetEvent =
    MailJetBaseEvent :+: MailJetUnsubscribeEvent :+: MailJetSpamEvent :+: MailJetBounceEvent :+: MailJetBlockedEvent :+: CNil

  val eventDecoderMap = Map(
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

final case class MailJetEventWrapper(version: Int, id: String, date: ZonedDateTime, event: AnyMailJetEvent)
    extends Sharded
object MailJetEventWrapper {
  def wrapEvent(event: MailJetEvent): AnyMailJetEvent = event match {
    case e: MailJetBaseEvent        => Coproduct[AnyMailJetEvent](e)
    case e: MailJetUnsubscribeEvent => Coproduct[AnyMailJetEvent](e)
    case e: MailJetSpamEvent        => Coproduct[AnyMailJetEvent](e)
    case e: MailJetBounceEvent      => Coproduct[AnyMailJetEvent](e)
    case e: MailJetBlockedEvent     => Coproduct[AnyMailJetEvent](e)
  }
}
object ToMailJetEvent extends Poly1 {
  implicit val atMailJetBaseEvent: Case.Aux[MailJetBaseEvent, MailJetBaseEvent] = at(identity)
  implicit val atMailJetUnsubscribeEvent: Case.Aux[MailJetUnsubscribeEvent, MailJetUnsubscribeEvent] = at(identity)
  implicit val atMailJetSpamEvent: Case.Aux[MailJetSpamEvent, MailJetSpamEvent] = at(identity)
  implicit val atMailJetBounceEvent: Case.Aux[MailJetBounceEvent, MailJetBounceEvent] = at(identity)
  implicit val atMailJetBlockedEvent: Case.Aux[MailJetBlockedEvent, MailJetBlockedEvent] = at(identity)
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

sealed trait MailJetError {
  def relatedTo: MailJetErrorRelatedTo
  def name: String
}
object MailJetError extends StrictLogging {

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

case class MailJetBaseEvent(event: String,
                            override val email: String,
                            override val time: Option[Long],
                            override val messageId: Option[Long],
                            override val campaignId: Option[Int],
                            override val contactId: Option[Int],
                            override val customCampaign: Option[String],
                            override val customId: Option[String],
                            override val payload: Option[String])
    extends MailJetEvent
object MailJetBaseEvent {
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
    (event: String,
     email: String,
     time: Option[Long],
     messageId: Option[Long],
     campaignId: Option[Int],
     contactId: Option[Int],
     customCampaign: Option[String],
     customId: Option[String],
     payload: Option[String]) =>
      MailJetBaseEvent(
        event = event,
        email = email,
        time = time,
        messageId = messageId,
        customCampaign = customCampaign,
        campaignId = campaignId,
        contactId = contactId,
        customId = customId,
        payload = payload
      )
  }
}

case class MailJetBounceEvent(override val email: String,
                              override val time: Option[Long] = None,
                              override val messageId: Option[Long] = None,
                              override val campaignId: Option[Int] = None,
                              override val contactId: Option[Int] = None,
                              override val customCampaign: Option[String] = None,
                              override val customId: Option[String] = None,
                              override val payload: Option[String] = None,
                              blocked: Boolean,
                              hardBounce: Boolean,
                              error: Option[MailJetError])
    extends MailJetEvent

object MailJetBounceEvent {
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
    (email: String,
     time: Option[Long],
     messageId: Option[Long],
     campaignId: Option[Int],
     contactId: Option[Int],
     customCampaign: Option[String],
     customId: Option[String],
     payload: Option[String],
     blocked: Boolean,
     hardBounce: Boolean,
     errorRelatedTo: Option[String],
     error: Option[String]) =>
      MailJetBounceEvent(
        email = email,
        time = time,
        messageId = messageId,
        customCampaign = customCampaign,
        campaignId = campaignId,
        contactId = contactId,
        customId = customId,
        payload = payload,
        blocked = blocked,
        hardBounce = hardBounce,
        error = MailJetError.getError(error, errorRelatedTo)
      )

  }
}

case class MailJetBlockedEvent(override val email: String,
                               override val time: Option[Long] = None,
                               override val messageId: Option[Long] = None,
                               override val campaignId: Option[Int] = None,
                               override val contactId: Option[Int] = None,
                               override val customCampaign: Option[String] = None,
                               override val customId: Option[String] = None,
                               override val payload: Option[String] = None,
                               error: Option[MailJetError])
    extends MailJetEvent
object MailJetBlockedEvent {
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
    (email: String,
     time: Option[Long],
     messageId: Option[Long],
     campaignId: Option[Int],
     contactId: Option[Int],
     customCampaign: Option[String],
     customId: Option[String],
     payload: Option[String],
     errorRelatedTo: Option[String],
     error: Option[String]) =>
      MailJetBlockedEvent(
        email = email,
        time = time,
        messageId = messageId,
        customCampaign = customCampaign,
        campaignId = campaignId,
        contactId = contactId,
        customId = customId,
        payload = payload,
        error = MailJetError.getError(error, errorRelatedTo)
      )
  }
}

case class MailJetSpamEvent(override val email: String,
                            override val time: Option[Long] = None,
                            override val messageId: Option[Long] = None,
                            override val campaignId: Option[Int] = None,
                            override val contactId: Option[Int] = None,
                            override val customCampaign: Option[String] = None,
                            override val customId: Option[String] = None,
                            override val payload: Option[String] = None,
                            source: Option[String])
    extends MailJetEvent
object MailJetSpamEvent {
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
    (email: String,
     time: Option[Long],
     messageId: Option[Long],
     campaignId: Option[Int],
     contactId: Option[Int],
     customCampaign: Option[String],
     customId: Option[String],
     payload: Option[String],
     source: Option[String]) =>
      MailJetSpamEvent(
        email = email,
        time = time,
        messageId = messageId,
        campaignId = campaignId,
        contactId = contactId,
        customCampaign = customCampaign,
        customId = customId,
        payload = payload,
        source = source
      )
  }
}

case class MailJetUnsubscribeEvent(email: String,
                                   time: Option[Long] = None,
                                   messageId: Option[Long] = None,
                                   campaignId: Option[Int] = None,
                                   contactId: Option[Int] = None,
                                   customCampaign: Option[String] = None,
                                   customId: Option[String] = None,
                                   payload: Option[String] = None,
                                   listId: Option[Int],
                                   ip: Option[String],
                                   geo: Option[String],
                                   agent: Option[String])
    extends MailJetEvent
object MailJetUnsubscribeEvent {
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
    (email: String,
     time: Option[Long],
     messageId: Option[Long],
     campaignId: Option[Int],
     contactId: Option[Int],
     customCampaign: Option[String],
     customId: Option[String],
     payload: Option[String],
     listId: Option[Int],
     ip: Option[String],
     geo: Option[String],
     agent: Option[String]) =>
      MailJetUnsubscribeEvent(
        email = email,
        time = time,
        messageId = messageId,
        campaignId = campaignId,
        contactId = contactId,
        customCampaign = customCampaign,
        customId = customId,
        payload = payload,
        listId = listId,
        ip = ip,
        geo = geo,
        agent = agent
      )

  }
}
