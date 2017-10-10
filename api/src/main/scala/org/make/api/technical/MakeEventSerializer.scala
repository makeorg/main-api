package org.make.api.technical

import org.make.api.proposal.ProposalSerializers
import org.make.api.sessionhistory.SessionHistorySerializers
import org.make.api.sequence.SequenceSerializers
import org.make.api.technical.MakeEventSerializer.allSerializers
import org.make.api.userhistory.UserHistorySerializers
import stamina.{Persister, StaminaAkkaSerializer}

class MakeEventSerializer extends StaminaAkkaSerializer(allSerializers.head, allSerializers.tail: _*)

object MakeEventSerializer {
  val allSerializers: Seq[Persister[_, _]] =
    ProposalSerializers.serializers ++
      UserHistorySerializers.serializers ++
      SessionHistorySerializers.serializers ++
      SequenceSerializers.serializers
}
