package org.make.core.proposition

import java.time.ZonedDateTime

import org.make.core.StringValue
import org.make.core.citizen.CitizenId

case class Proposition (
                         propositionId: PropositionId,
                         citizenId: CitizenId,
                         createdAt: ZonedDateTime,
                         updatedAt: ZonedDateTime,
                         content: String
                       )

case class PropositionId(value: String) extends StringValue
