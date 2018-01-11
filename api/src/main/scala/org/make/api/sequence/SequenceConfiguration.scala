package org.make.api.sequence

import com.typesafe.scalalogging.StrictLogging
import org.make.core.sequence.SequenceId
import akka.pattern.ask
import akka.util.Timeout
import io.circe.{Decoder, Encoder}

import scala.concurrent.duration.DurationInt
import org.make.api.sequence.SequenceConfigurationActor.{
  GetPersistentSequenceConfiguration,
  GetSequenceConfiguration,
  SetSequenceConfiguration
}
import io.circe.generic.semiauto._

import scala.concurrent.Future

case class SequenceConfiguration(sequenceId: SequenceId,
                                 newProposalsRatio: Double,
                                 newProposalsVoteThreshold: Int,
                                 testedProposalsEngagementThreshold: Double,
                                 banditEnabled: Boolean,
                                 banditMinCount: Int,
                                 banditProposalsRatio: Double)

object SequenceConfiguration {
  implicit val decoder: Decoder[SequenceConfiguration] = deriveDecoder[SequenceConfiguration]
  implicit val encoder: Encoder[SequenceConfiguration] = deriveEncoder[SequenceConfiguration]
}

trait SequenceConfigurationService {
  def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration]
  def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
  def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
}

trait SequenceConfigurationComponent {
  val sequenceConfigurationService: SequenceConfigurationService
}

trait DefaultSequenceConfigurationComponent extends SequenceConfigurationComponent with StrictLogging {
  self: SequenceConfigurationActorComponent =>

  implicit val timeout: Timeout = Timeout(3.seconds)

  override lazy val sequenceConfigurationService: SequenceConfigurationService = new SequenceConfigurationService {
    override def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration] = {
      (sequenceConfigurationActor ? GetSequenceConfiguration(sequenceId)).mapTo[SequenceConfiguration]
    }

    override def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean] = {
      (sequenceConfigurationActor ? SetSequenceConfiguration(sequenceConfiguration)).mapTo[Boolean]
    }

    override def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
      (sequenceConfigurationActor ? GetPersistentSequenceConfiguration(sequenceId)).mapTo[Option[SequenceConfiguration]]
    }
  }
}
