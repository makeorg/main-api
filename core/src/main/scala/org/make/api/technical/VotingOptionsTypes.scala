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

package org.make.api.technical.types

import org.make.core.proposal.{BaseQualification, BaseVote}

sealed trait VotingOptionWrapper

final case class AgreeWrapper(
  vote: BaseVote,
  likeIt: BaseQualification,
  platitudeAgree: BaseQualification,
  doable: BaseQualification
) extends VotingOptionWrapper

final case class NeutralWrapper(
  vote: BaseVote,
  noOpinion: BaseQualification,
  doNotUnderstand: BaseQualification,
  doNotCare: BaseQualification
) extends VotingOptionWrapper

final case class DisagreeWrapper(
  vote: BaseVote,
  impossible: BaseQualification,
  noWay: BaseQualification,
  platitudeDisagree: BaseQualification
) extends VotingOptionWrapper

final case class VotingOptions(agreeVote: AgreeWrapper, neutralVote: NeutralWrapper, disagreeVote: DisagreeWrapper)
