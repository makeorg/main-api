package org.make.api.proposal

import java.time.ZonedDateTime
import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.api.{DockerElasticsearchService, ItMakeTest}
import org.make.core.proposal._
import org.make.core.user.UserId
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.Failure
import io.circe.syntax._
import io.circe.generic.auto._
import org.make.core.CirceFormatters
import org.make.core.proposal.indexed._

class ProposalSearchEngineIT
    extends ItMakeTest
    with CirceFormatters
    with DockerElasticsearchService
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationComponent {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  Mockito.when(elasticsearchConfiguration.host).thenReturn("localhost")
  Mockito.when(elasticsearchConfiguration.port).thenReturn(defaultElasticsearchPortExposed)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startAllOrFail()
    initializeElasticsearch()
  }

  private def initializeElasticsearch(): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val databaseEndpoint = s"http://localhost:$defaultElasticsearchPortExposed"

    // register index
    val proposalMapping = Source.fromResource("proposal-mapping.json").getLines().mkString("")
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = s"$databaseEndpoint/$defaultElasticsearchIndex",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposalMapping)
        )
      )
    Await.result(responseFuture, 5.seconds)

    // inserting data
    val insertFutures: Future[Seq[HttpResponse]] = Future.sequence(proposals.map { proposal: IndexedProposal =>
      val indexAndDocTypeEndpoint = s"$defaultElasticsearchIndex/$defaultElasticsearchDocType"

      Http().singleRequest(
        HttpRequest(
          uri = s"$databaseEndpoint/$indexAndDocTypeEndpoint/${proposal.id.value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, proposal.asJson.toString)
        )
      )
    })

    Await.result(insertFutures, 150.seconds)
    insertFutures.onComplete {
      case Failure(e) => fail(e)
      case _          =>
    }
  }

  private val now = ZonedDateTime.now()
  private val newProposal = IndexedProposal(
    id = ProposalId(UUID.randomUUID().toString),
    userId = UserId("user-id"),
    content = "This is a test proposal",
    slug = "this-is-a-test-proposal",
    createdAt = now,
    updatedAt = None,
    votesAgree = Vote(key = VoteKey.Agree, qualifications = Seq()),
    votesDisagree = Vote(key = VoteKey.Disagree, qualifications = Seq()),
    votesNeutral = Vote(key = VoteKey.Neutral, qualifications = Seq()),
    proposalContext = ProposalContext(operation = None, location = None, question = None, source = None),
    author = Author(firstName = None, postalCode = None, age = None),
    themeId = None,
    tags = Seq.empty,
    trending = None,
    labels = Seq(),
    country = "FR",
    language = "fr",
    status = ProposalStatus.Accepted
  )

  private val acceptedProposals: Seq[IndexedProposal] = Seq(
    IndexedProposal(
      id = ProposalId("f4b02e75-8670-4bd0-a1aa-6d91c4de968a"),
      country = "FR",
      language = "fr",
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut que mon/ma député(e) fasse la promotion de la permaculture",
      slug = "il-faut-que-mon-ma-depute-fasse-la-promotion-de-la-permaculture",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 123, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 105, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 59, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Craig"), postalCode = Some("92876"), age = Some(25)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    ),
    IndexedProposal(
      id = ProposalId("9c468c22-1d1a-474b-9081-d79f1079f5e5"),
      country = "FR",
      language = "fr",
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content = "Il faut qu'il/elle interdise les élevages et cultures intensives",
      slug = "il-faut-qu-il-elle-interdise-les-elevages-et-cultures-intensives",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 79, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 104, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 127, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Valerie"), postalCode = Some("41556"), age = Some(26)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    ),
    IndexedProposal(
      id = ProposalId("ed8d8b66-579a-48bd-9f61-b7f6cf679e95"),
      country = "FR",
      language = "fr",
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content = "Il faut qu'il/elle privilégie les petites exploitations agricoles aux fermes usines",
      slug = "il-faut-qu-il-elle-privilegie-les-petites-exploitations-agricoles-aux-fermes-usines",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 56, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 18, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 53, qualifications = Seq()),
      status = ProposalStatus.Accepted,
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Valerie"), postalCode = Some("41556"), age = Some(26)),
      themeId = None,
      tags = Seq()
    ),
    IndexedProposal(
      id = ProposalId("c700b4c0-1b49-4373-a993-23c2437e857a"),
      country = "FR",
      language = "fr",
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content =
        "Il faut qu'il/elle protège notre agriculture locale et donne les moyens aux agriculteurs de vivre de leur métier de production",
      slug =
        "il-faut-qu-il-elle-protege-notre-agriculture-locale-et-donne-les-moyens-aux-agriculteurs-de-vivre-de-leur-metier-de-production",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 152, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 78, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 123, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Jennifer"), postalCode = Some("40734"), age = Some(23)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    ),
    IndexedProposal(
      id = ProposalId("eac55aab-021e-495e-9664-bea941b8c51c"),
      country = "FR",
      language = "fr",
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "Il faut qu'il/elle favorise l'accès à l'alimentation issue de l'agriculture biologique",
      slug = "il-faut-qu-il-elle-favorise-l-acces-a-l-alimentation-issue-de-l-agriculture-biologique",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 175, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 70, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 123, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Laura"), postalCode = Some("43324"), age = Some(31)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    ),
    IndexedProposal(
      id = ProposalId("5725e8fc-54a1-4b77-9246-d1de60a245c5"),
      country = "FR",
      language = "fr",
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content =
        "Il faut qu'il/elle dissolve la SAFER et ainsi laisser les petits paysans s'installer, avec des petites exploitations",
      slug =
        "il-faut-qu-il-elle-dissolve-la-SAFER-et-ainsi-laisser-les-petits-paysans-s-installer-avec-des-petites-exploitations",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 48, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 70, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 187, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Laura"), postalCode = Some("43324"), age = Some(31)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    ),
    IndexedProposal(
      id = ProposalId("d38244bc-3d39-44a2-bfa9-a30158a297a3"),
      country = "FR",
      language = "fr",
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "Il faut qu'il/elle soutienne et défende l'agriculture dans mon département",
      slug = "il-faut-qu-il-elle-soutienne-et-defende-l-agriculture-dans-mon-departement",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 60, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 56, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 170, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Laura"), postalCode = Some("43324"), age = Some(31)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    ),
    IndexedProposal(
      id = ProposalId("ddba011d-5950-4237-bdf1-8bf25473f366"),
      country = "FR",
      language = "fr",
      userId = UserId("c0cbad58-b143-492d-8895-1b9c5dbe48bb"),
      content = "Il faut qu'il/elle privilégie les producteurs locaux pour les cantines et repas à domicile.",
      slug = "il-faut-qu-il-elle-privilegie-les-producteurs-locaux-pour-les-cantines-et-repas-a-domicile",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 95, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 32, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 35, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Laura"), postalCode = Some("43324"), age = Some(31)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Accepted
    )
  )

  private val pendingProposals: Seq[IndexedProposal] = Seq(
    IndexedProposal(
      id = ProposalId("7413c8dd-9b17-44be-afc8-fb2898b12773"),
      country = "FR",
      language = "fr",
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content =
        "Il faut qu'il/elle favorise l'agriculture qualitative plut\\u00f4t que l'agriculture intensive (plus de pesticides pour plus de rendements)",
      slug =
        "il-faut-qu-il-elle-favorise-l-agriculture-qualitative-plutot-que-l-agriculture-intensive-plus-de-pesticides-pour-plus-de-rendements",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 37, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 66, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 75, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Ronald"), postalCode = Some("41556"), age = Some(26)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    ),
    IndexedProposal(
      id = ProposalId("3bd7ae66-d2b4-42c2-96dd-46dbdb477797"),
      country = "FR",
      language = "fr",
      userId = UserId("ef418fad-2d2c-4f49-9b36-bf9d6f282aa2"),
      content =
        "Il faut qu'il/elle vote une loi pour obliger l'industrie pharmaceutique d'investir dans la recherche sur les maladies rares",
      slug =
        "il-faut-qu-il-elle-vote-une-loi-pour-obliger-l-industrie-pharmaceutique-d-investir-dans-la-recherche-sur-les-maladies-rares",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 67, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 42, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 22, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Jennifer"), postalCode = Some("81966"), age = Some(21)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    ),
    IndexedProposal(
      id = ProposalId("bd44db77-3096-4e3b-b539-a4038307d85e"),
      country = "FR",
      language = "fr",
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content =
        "Il faut qu'il/elle propose d'interdire aux politiques l'utilisation du big data menant à faire des projets démagogiques",
      slug =
        "il-faut-qu-il-elle-propose-d-interdire-aux-politiques-l-utilisation-du-big-data-menant-a-faire-des-projets-demagogiques",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 116, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 167, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 73, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Ronald"), postalCode = Some("40734"), age = Some(23)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    ),
    IndexedProposal(
      id = ProposalId("f2153c81-c031-41f0-8b02-c6ed556d62aa"),
      country = "FR",
      language = "fr",
      userId = UserId("ef418fad-2d2c-4f49-9b36-bf9d6f282aa2"),
      content =
        "Il faut qu'il/elle mette en avant la création de lieux de culture et d'échange, avec quelques petites subventions",
      slug =
        "Il-faut-qu-il-elle-mette-en-avant-la-creation-de-lieux-de-culture-et-d-echange-avec-quelques-petites-subventions",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 86, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 165, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 96, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Jennifer"), postalCode = Some("81966"), age = Some(21)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    ),
    IndexedProposal(
      id = ProposalId("13b16b9c-9293-4d33-9b82-415264820639"),
      country = "FR",
      language = "fr",
      userId = UserId("463e2937-42f4-4a18-9555-0a962531a55f"),
      content = "Il faut qu'il/elle défende un meilleur accès à la culture et à l'éducation pour tous.",
      slug = "il-faut-qu-il-elle-defende-un-meilleur-acces-a-la-culture-et-a-l-education-pour-tous",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 170, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 33, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 64, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Craig"), postalCode = Some("40734"), age = Some(23)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    ),
    IndexedProposal(
      id = ProposalId("b3198ad3-ff48-49f2-842c-2aefc3d0df5d"),
      country = "FR",
      language = "fr",
      userId = UserId("1036d603-8f1a-40b7-8a43-82bdcda3caf5"),
      content = "Il faut qu'il/elle pratique le mécennat et crée des aides pour les artistes, surtout les jeunes.",
      slug = "il-faut-qu-il-elle-pratique-le-mecennat-et-cree-des-aides-pour-les-artistes-surtout-les-jeunes",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 17, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 119, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 68, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Valerie"), postalCode = Some("92876"), age = Some(25)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    ),
    IndexedProposal(
      id = ProposalId("cf940085-010d-46de-8bfd-dee7e8adc8b6"),
      country = "FR",
      language = "fr",
      userId = UserId("fb600b89-0e04-419a-9f16-4c3311d2c53a"),
      content = "Il faut qu'il/elle défende la francophonie dans le monde en luttant contre l'hégémonie de l'anglais",
      slug = "il-elle-defende-la-francophonie-dans-le-monde-en-luttant-contre-l-hegemonie-de-l-anglais",
      createdAt = ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z")),
      updatedAt = Some(ZonedDateTime.from(dateFormatter.parse("2017-06-02T01:01:01.123Z"))),
      votesAgree = Vote(key = VoteKey.Agree, count = 124, qualifications = Seq()),
      votesDisagree = Vote(key = VoteKey.Disagree, count = 74, qualifications = Seq()),
      votesNeutral = Vote(key = VoteKey.Neutral, count = 56, qualifications = Seq()),
      proposalContext = ProposalContext(source = None, operation = None, location = None, question = None),
      trending = None,
      labels = Seq(),
      author = Author(firstName = Some("Craig"), postalCode = Some("41556"), age = Some(26)),
      themeId = None,
      tags = Seq(),
      status = ProposalStatus.Pending
    )
  )

  private val proposals: Seq[IndexedProposal] = acceptedProposals ++ pendingProposals

  feature("get proposal by id") {
    val proposalId = proposals.head.id
    scenario("should return a proposal") {
      whenReady(elasticsearchAPI.findProposalById(proposalId), Timeout(3.seconds)) {
        case Some(proposal) =>
          proposal.id should equal(proposalId)
        case None => fail("proposal not found by id")
      }
    }
  }

  feature("search proposals by content") {
    Given("searching by keywords")
    val query =
      SearchQuery(filter = Some(SearchFilter(content = Some(ContentSearchFilter(text = "Il faut que")))))
    scenario("should return a list of proposals") {
      whenReady(elasticsearchAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.length should be > 0
      }
    }
  }

  feature("empty query returns accepted proposals only") {
    Given("searching without query")
    val query = SearchQuery()
    scenario("should return a list of accepted proposals") {
      whenReady(elasticsearchAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        result.length should be(acceptedProposals.size)
      }
    }
  }

  feature("search proposals by status") {
    Given("searching pending proposals")
    val query = SearchQuery(Some(SearchFilter(status = Some(StatusSearchFilter(ProposalStatus.Pending)))))
    scenario("should return a list of pending proposals") {
      whenReady(elasticsearchAPI.searchProposals(query), Timeout(3.seconds)) { result =>
        info(result.map(_.status).mkString)
        result.length should be(pendingProposals.size)
      }
    }
  }

  feature("saving new proposal") {
    scenario("should return done") {
      whenReady(elasticsearchAPI.indexProposal(newProposal), Timeout(3.seconds)) { result =>
        result should be(Done)
      }
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stopAllQuietly()
  }
}
