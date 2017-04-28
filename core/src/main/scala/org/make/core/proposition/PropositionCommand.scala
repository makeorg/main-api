package org.make.core.proposition

import org.make.core.citizen.CitizenId

sealed trait PropositionCommand {
  def propositionId: PropositionId
}

case class ProposeCommand(
                           propositionId: PropositionId,
                           citizenId: CitizenId,
                           content: String
                         ) extends PropositionCommand

case class UpdatePropositionCommand(propositionId: PropositionId, content: String) extends PropositionCommand

case class ViewPropositionCommand(propositionId: PropositionId) extends PropositionCommand

case class GetProposition(propositionId: PropositionId) extends PropositionCommand
