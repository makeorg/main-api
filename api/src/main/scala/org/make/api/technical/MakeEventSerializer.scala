package org.make.api.technical

import org.make.api.citizen.CitizenSerializers
import org.make.api.proposition.PropositionSerializers
import org.make.api.technical.MakeEventSerializer.allSerializers
import org.make.api.vote.VoteSerializers
import stamina.{Persister, StaminaAkkaSerializer}

class MakeEventSerializer
    extends StaminaAkkaSerializer(
      allSerializers.head,
      allSerializers.tail:_*
    )

object MakeEventSerializer {
  val allSerializers: Seq[Persister[_, _]] =
    CitizenSerializers.serializers ++
      PropositionSerializers.serializers ++
      VoteSerializers.serializers
}
