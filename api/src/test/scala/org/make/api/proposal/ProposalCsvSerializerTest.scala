package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.MakeApiTestBase
import org.make.api.technical.auth.MakeAuthentication
import org.make.core.idea.IdeaId
import org.make.core.operation.{Operation, OperationId, OperationStatus, OperationTranslation}
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.reference.{Theme, ThemeId, ThemeTranslation}
import org.make.core.user.UserId

import scala.collection.immutable.Seq

class ProposalCsvSerializerTest extends MakeApiTestBase with MakeAuthentication {

  val theme: Theme =
    Theme(ThemeId("foo-theme"), Seq(ThemeTranslation("foo-theme", "Foo Theme", "fr")), 42, 42, 42, "FR", "#0ff")
  val operation: Operation = Operation(
    OperationStatus.Active,
    OperationId("bar-operation"),
    "bar-operation",
    Seq(OperationTranslation("Bar Operation", "fr")),
    "fr",
    List.empty,
    None,
    None,
    Seq.empty
  )
  val now: ZonedDateTime = ZonedDateTime.now()
  val nowRepresentation: String = now.toLocalDateTime.toString.split("T").mkString(" - ")

  val proposals: Seq[IndexedProposal] = Seq(
    IndexedProposal(
      id = ProposalId("11111111-1111-1111-1111-111111111111"),
      country = "FR",
      language = "fr",
      userId = UserId("11111111-2222-3333-4444-555555555555"),
      content = "Il faut que ma proposition soit au format CSV.",
      slug = "il-faut-que-ma-proposition-soit-au-format-csv",
      createdAt = now,
      updatedAt = Some(now),
      votes = Seq(
        IndexedVote(
          key = VoteKey.Agree,
          count = 123,
          qualifications = Seq(
            IndexedQualification(QualificationKey.LikeIt, 1),
            IndexedQualification(QualificationKey.Doable, 2),
            IndexedQualification(QualificationKey.PlatitudeAgree, 3)
          )
        ),
        IndexedVote(
          key = VoteKey.Disagree,
          count = 105,
          qualifications = Seq(
            IndexedQualification(QualificationKey.NoWay, 42),
            IndexedQualification(QualificationKey.Impossible, 42),
            IndexedQualification(QualificationKey.PlatitudeDisagree, 42)
          )
        ),
        IndexedVote(
          key = VoteKey.Neutral,
          count = 59,
          qualifications = Seq(
            IndexedQualification(QualificationKey.DoNotUnderstand, 7),
            IndexedQualification(QualificationKey.NoOpinion, 8),
            IndexedQualification(QualificationKey.DoNotCare, 9)
          )
        )
      ),
      context = Some(Context(source = None, operation = None, location = None, question = None)),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Jenna"), postalCode = Some("000000"), age = Some(25)),
      themeId = Some(theme.themeId),
      tags = Seq(),
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id")),
      operationId = None
    ),
    IndexedProposal(
      id = ProposalId("22222222-2222-2222-2222-222222222222"),
      country = "FR",
      language = "fr",
      userId = UserId("22222222-3333-4444-5555-666666666666"),
      content = "Il faut que ma proposition d'opération soit en CSV.",
      slug = "il-faut-que-ma-proposition-d-operation-soit-en-csv",
      createdAt = now,
      updatedAt = Some(now),
      votes = Seq(
        IndexedVote(
          key = VoteKey.Agree,
          count = 79,
          qualifications = Seq(
            IndexedQualification(QualificationKey.LikeIt, 21),
            IndexedQualification(QualificationKey.Doable, 84),
            IndexedQualification(QualificationKey.PlatitudeAgree, 14)
          )
        ),
        IndexedVote(
          key = VoteKey.Disagree,
          count = 104,
          qualifications = Seq(
            IndexedQualification(QualificationKey.NoWay, 42),
            IndexedQualification(QualificationKey.Impossible, 42),
            IndexedQualification(QualificationKey.PlatitudeDisagree, 42)
          )
        ),
        IndexedVote(
          key = VoteKey.Neutral,
          count = 127,
          qualifications = Seq(
            IndexedQualification(QualificationKey.DoNotUnderstand, 1),
            IndexedQualification(QualificationKey.NoOpinion, 2),
            IndexedQualification(QualificationKey.DoNotCare, 3)
          )
        )
      ),
      context = Some(Context(source = None, operation = None, location = None, question = None)),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("John"), postalCode = Some("11111"), age = Some(26)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted,
      ideaId = Some(IdeaId("idea-id")),
      operationId = Some(operation.operationId)
    )
  )

  feature("proposals to csv") {
    scenario("call proposalsToRow") {
      Given("a theme, an operation and a proposal in each")
      When("we call proposalToRow")
      val rows = ProposalCsvSerializer.proposalsToRow(proposals, Seq(theme), Seq(operation))

      Then("we have the proposals as csv")
      rows.head should be(
        s""""Foo Theme",,"","Il faut que ma proposition soit au format CSV.","Jenna","",287,123,1,2,3,105,42,42,42,59,7,8,9,000000,25,11111111-2222-3333-4444-555555555555,$nowRepresentation,$nowRepresentation"""
      )
      rows(1) should be(
        s""","Bar Operation","","Il faut que ma proposition d'opération soit en CSV.","John","",310,79,21,84,14,104,42,42,42,127,1,2,3,11111,26,22222222-3333-4444-5555-666666666666,$nowRepresentation,$nowRepresentation"""
      )
    }
  }

  feature("vote to csv") {
    scenario("call voteToRow") {
      Given("A voted proposal")
      When("we call voteToRow")
      Then("we see the votes numbers")
      ProposalCsvSerializer.votesToRow(proposals.head.votes) should be("287,123,1,2,3,105,42,42,42,59,7,8,9")
    }
  }

}
