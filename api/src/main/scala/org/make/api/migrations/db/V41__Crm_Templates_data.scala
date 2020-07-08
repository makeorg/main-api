/*
 *  Make.org Core API
 *  Copyright (C) 2018-2019 Make.org
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

package org.make.api.migrations.db

import java.sql.Connection
import java.util.UUID

class V41__Crm_Templates_data extends Migration {

  @SuppressWarnings(Array("org.wartremover.warts.While"))
  override def migrate(connection: Connection): Unit = {
    val isProd = System.getenv("ENV_NAME") == "prod"

    val resultSet = connection
      .prepareStatement("SELECT question_id, slug FROM question")
      .executeQuery()

    var questions: Map[String, String] = Map.empty

    while (resultSet.next()) {
      val questionId = resultSet.getString("question_id")
      val slug = resultSet.getString("slug")
      questions += (slug -> questionId)
    }

    def nextId(): String = UUID.randomUUID().toString

    def valuesToInsertCrmTemplate(
      questionId: String,
      locale: String,
      registration: String,
      welcome: String,
      accepted: String,
      refused: String,
      forgotPassword: String,
      acceptedOrganisation: String,
      refusedOrganisation: String,
      forgotPasswordOrganisation: String
    ): String = {
      val id: String = nextId()
      (s"('$id', '$questionId', '$locale', '$registration', '$welcome'," +
        s" '$accepted', '$refused', '$forgotPassword'," +
        s" '$acceptedOrganisation', '$refusedOrganisation'," +
        s" '$forgotPasswordOrganisation')").replace("''", "null")
    }

    val templatesIdsLocales =
      if (isProd) CrmTemplateRawDataProd.templatesIdsLocales else CrmTemplateRawData.templatesIdsLocales
    val templatesIdsQuestions =
      if (isProd) CrmTemplateRawDataProd.templatesIdsQuestions else CrmTemplateRawData.templatesIdsQuestions

    val valuesToInsert = (templatesIdsLocales.map {
      case (locale, templates) =>
        valuesToInsertCrmTemplate(
          questionId = "",
          locale = locale,
          registration = templates.getOrElse("registration", ""),
          welcome = templates.getOrElse("welcome", ""),
          accepted = templates.getOrElse("proposal-accepted", ""),
          refused = templates.getOrElse("proposal-refused", ""),
          forgotPassword = templates.getOrElse("forgotten-password", ""),
          acceptedOrganisation = templates.getOrElse("acceptedOrganisation", ""),
          refusedOrganisation = templates.getOrElse("refusedOrganisation", ""),
          forgotPasswordOrganisation = templates.getOrElse("forgotPasswordOrganisation", "")
        )
    } ++ templatesIdsQuestions.filter {
      case (question, _) => questions.contains(question)
    }.map {
      case (question, templates) =>
        valuesToInsertCrmTemplate(
          questionId = questions.getOrElse(question, ""),
          locale = "",
          registration = templates.getOrElse("registration", ""),
          welcome = templates.getOrElse("welcome", ""),
          accepted = templates.getOrElse("proposal-accepted", ""),
          refused = templates.getOrElse("proposal-refused", ""),
          forgotPassword = templates.getOrElse("forgotten-password", ""),
          acceptedOrganisation = templates.getOrElse("acceptedOrganisation", ""),
          refusedOrganisation = templates.getOrElse("refusedOrganisation", ""),
          forgotPasswordOrganisation = templates.getOrElse("forgotPasswordOrganisation", "")
        )
    }).mkString(", ")

    val statement =
      connection.prepareStatement(
        "INSERT INTO crm_templates (id, question_id, locale, registration, welcome, proposal_accepted, " +
          "proposal_refused, forgotten_password, proposal_accepted_organisation, proposal_refused_organisation, " +
          s"forgotten_password_organisation) VALUES  $valuesToInsert"
      )
    statement.execute()

  }
}

object CrmTemplateRawData {
  val templatesIdsLocales: Map[String, Map[String, String]] = Map(
    "fr_FR" ->
      Map(
        "registration" -> "225362",
        "welcome" -> "235799",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "en_GB" ->
      Map(
        "registration" -> "313838",
        "welcome" -> "313850",
        "proposal-refused" -> "313868",
        "proposal-accepted" -> "313860",
        "forgotten-password" -> "313871",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "it_IT" ->
      Map(
        "registration" -> "313847",
        "welcome" -> "313857",
        "proposal-refused" -> "313876",
        "proposal-accepted" -> "313865",
        "forgotten-password" -> "313880",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "de_DE" ->
      Map(
        "registration" -> "225362",
        "welcome" -> "235799",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "561085",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      )
  )

  val templatesIdsQuestions: Map[String, Map[String, String]] = Map(
    "vff" ->
      Map(
        "registration" -> "228778",
        "welcome" -> "239520",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "vff-gb" ->
      Map(
        "registration" -> "313838",
        "welcome" -> "313850",
        "proposal-refused" -> "313868",
        "proposal-accepted" -> "313860",
        "forgotten-password" -> "313871",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "vff-it" ->
      Map(
        "registration" -> "313847",
        "welcome" -> "313857",
        "proposal-refused" -> "313876",
        "proposal-accepted" -> "313865",
        "forgotten-password" -> "313880",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "climatparis" ->
      Map(
        "registration" -> "283908",
        "welcome" -> "283902",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "lpae" ->
      Map(
        "registration" -> "308018",
        "welcome" -> "308023",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "mieux-vivre-ensemble" ->
      Map(
        "registration" -> "315238",
        "welcome" -> "315246",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "chance-aux-jeunes" ->
      Map(
        "registration" -> "345560",
        "welcome" -> "345561",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "culture" ->
      Map(
        "registration" -> "410415",
        "welcome" -> "410418",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "aines" ->
      Map(
        "registration" -> "535941",
        "welcome" -> "535949",
        "proposal-refused" -> "225359",
        "proposal-accepted" -> "225358",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "politique-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "economie-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "international-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "culture-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "ecologie-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "societe-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "education-huffpost" ->
      Map(
        "registration" -> "525182",
        "welcome" -> "525186",
        "proposal-refused" -> "525223",
        "proposal-accepted" -> "525225",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "plan-climat" ->
      Map(
        "registration" -> "566592",
        "welcome" -> "560753",
        "proposal-refused" -> "560768",
        "proposal-accepted" -> "560761",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "villededemain" ->
      Map(
        "registration" -> "566597",
        "welcome" -> "560753",
        "proposal-refused" -> "560768",
        "proposal-accepted" -> "560761",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "cityoftomorrow" ->
      Map(
        "registration" -> "313838",
        "welcome" -> "313850",
        "proposal-refused" -> "313868",
        "proposal-accepted" -> "313860",
        "forgotten-password" -> "313871",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "european-digital-champions" ->
      Map(
        "registration" -> "560736",
        "welcome" -> "560753",
        "proposal-refused" -> "560768",
        "proposal-accepted" -> "560761",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "european-digital-champions-de" ->
      Map(
        "registration" -> "561049",
        "welcome" -> "561056",
        "proposal-refused" -> "561061",
        "proposal-accepted" -> "561076",
        "forgotten-password" -> "561085",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "jeunesse-hautsdefrance" ->
      Map(
        "registration" -> "566594",
        "welcome" -> "560753",
        "proposal-refused" -> "560768",
        "proposal-accepted" -> "560761",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      ),
    "ditp" ->
      Map(
        "registration" -> "566599",
        "welcome" -> "572938",
        "proposal-refused" -> "572939",
        "proposal-accepted" -> "572943",
        "forgotten-password" -> "225361",
        "refusedOrganisation" -> "393189",
        "acceptedOrganisation" -> "408740",
        "forgotPasswordOrganisation" -> "618010"
      )
  )

}

object CrmTemplateRawDataProd {
  val templatesIdsLocales: Map[String, Map[String, String]] = Map(
    "fr_FR" -> Map(
      "registration" -> "222475",
      "welcome" -> "235705",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "en_GB" -> Map(
      "registration" -> "313889",
      "welcome" -> "313893",
      "proposal-accepted" -> "313897",
      "proposal-refused" -> "313899",
      "forgotten-password" -> "313903",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "it_IT" -> Map(
      "registration" -> "313891",
      "welcome" -> "313894",
      "proposal-accepted" -> "313898",
      "proposal-refused" -> "313901",
      "forgotten-password" -> "313904",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "de_DE" -> Map(
      "registration" -> "222475",
      "welcome" -> "235705",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "561111",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    )
  )
  val templatesIdsQuestions: Map[String, Map[String, String]] = Map(
    "aines" -> Map(
      "registration" -> "535928",
      "welcome" -> "535932",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "313899",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "chance-aux-jeunes" -> Map(
      "registration" -> "345554",
      "welcome" -> "345551",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "cityoftomorrow" -> Map(
      "registration" -> "313889",
      "welcome" -> "313893",
      "proposal-accepted" -> "313897",
      "proposal-refused" -> "572949",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "climatparis" -> Map(
      "registration" -> "283873",
      "welcome" -> "283898",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "culture" -> Map(
      "registration" -> "410407",
      "welcome" -> "410413",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "culture-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "ditp" -> Map(
      "registration" -> "566687",
      "welcome" -> "579466",
      "proposal-accepted" -> "572951",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "ecologie-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "economie-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "economiebienveillante" -> Map(
      "registration" -> "724754",
      "welcome" -> "724801",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "education-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "european-digital-champions" -> Map(
      "registration" -> "560731",
      "welcome" -> "561028",
      "proposal-accepted" -> "561039",
      "proposal-refused" -> "561034",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "european-digital-champions-de" -> Map(
      "registration" -> "561108",
      "welcome" -> "561102",
      "proposal-accepted" -> "561091",
      "proposal-refused" -> "561098",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "gilets-jaunes" -> Map(
      "registration" -> "222475",
      "welcome" -> "235705",
      "proposal-accepted" -> "561039",
      "proposal-refused" -> "561034",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "handicap" -> Map(
      "registration" -> "837123",
      "welcome" -> "837132",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "international-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "jeunesse-hautsdefrance" -> Map(
      "registration" -> "566684",
      "welcome" -> "561028",
      "proposal-accepted" -> "561039",
      "proposal-refused" -> "561034",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "lpae" -> Map(
      "registration" -> "308026",
      "welcome" -> "308028",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "mieux-vivre-ensemble" -> Map(
      "registration" -> "315234",
      "welcome" -> "315229",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "mieuxmanger" -> Map(
      "registration" -> "724892",
      "welcome" -> "725405",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "plan-climat" -> Map(
      "registration" -> "566688",
      "welcome" -> "561028",
      "proposal-accepted" -> "561039",
      "proposal-refused" -> "561034",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "politique-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "prevention-jeunes" -> Map(
      "registration" -> "778678",
      "welcome" -> "778695",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "societe-huffpost" -> Map(
      "registration" -> "525340",
      "welcome" -> "525235",
      "proposal-accepted" -> "525346",
      "proposal-refused" -> "525342",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "vff" -> Map(
      "registration" -> "228774",
      "welcome" -> "240419",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "222555",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "vff-gb" -> Map(
      "registration" -> "313889",
      "welcome" -> "313893",
      "proposal-accepted" -> "313897",
      "proposal-refused" -> "313899",
      "forgotten-password" -> "313903",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "vff-it" -> Map(
      "registration" -> "313891",
      "welcome" -> "313894",
      "proposal-accepted" -> "313898",
      "proposal-refused" -> "313901",
      "forgotten-password" -> "313904",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "villededemain" -> Map(
      "registration" -> "566685",
      "welcome" -> "561028",
      "proposal-accepted" -> "561039",
      "proposal-refused" -> "561034",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-at" -> Map(
      "registration" -> "652661",
      "welcome" -> "652664",
      "proposal-accepted" -> "652716",
      "proposal-refused" -> "652721",
      "forgotten-password" -> "652667",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-be-fr" -> Map(
      "registration" -> "652757",
      "welcome" -> "652761",
      "proposal-accepted" -> "652767",
      "proposal-refused" -> "652768",
      "forgotten-password" -> "652763",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-be-nl" -> Map(
      "registration" -> "652773",
      "welcome" -> "652775",
      "proposal-accepted" -> "652789",
      "proposal-refused" -> "652792",
      "forgotten-password" -> "652780",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-bg" -> Map(
      "registration" -> "652795",
      "welcome" -> "652797",
      "proposal-accepted" -> "652802",
      "proposal-refused" -> "652810",
      "forgotten-password" -> "652799",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-cy" -> Map(
      "registration" -> "652869",
      "welcome" -> "652876",
      "proposal-accepted" -> "652886",
      "proposal-refused" -> "652891",
      "forgotten-password" -> "652881",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-cz" -> Map(
      "registration" -> "652898",
      "welcome" -> "652905",
      "proposal-accepted" -> "652913",
      "proposal-refused" -> "652915",
      "forgotten-password" -> "652910",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-de" -> Map(
      "registration" -> "652921",
      "welcome" -> "652927",
      "proposal-accepted" -> "652942",
      "proposal-refused" -> "652947",
      "forgotten-password" -> "652933",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-dk" -> Map(
      "registration" -> "652956",
      "welcome" -> "652958",
      "proposal-accepted" -> "652964",
      "proposal-refused" -> "652969",
      "forgotten-password" -> "652962",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-ee" -> Map(
      "registration" -> "652974",
      "welcome" -> "652978",
      "proposal-accepted" -> "652987",
      "proposal-refused" -> "652989",
      "forgotten-password" -> "652983",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-es" -> Map(
      "registration" -> "653001",
      "welcome" -> "653004",
      "proposal-accepted" -> "653012",
      "proposal-refused" -> "653014",
      "forgotten-password" -> "653007",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-fi" -> Map(
      "registration" -> "653021",
      "welcome" -> "653028",
      "proposal-accepted" -> "653037",
      "proposal-refused" -> "653041",
      "forgotten-password" -> "653031",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-fr" -> Map(
      "registration" -> "652552",
      "welcome" -> "652573",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "708026",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-gr" -> Map(
      "registration" -> "653046",
      "welcome" -> "653049",
      "proposal-accepted" -> "653054",
      "proposal-refused" -> "653056",
      "forgotten-password" -> "653050",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-hr" -> Map(
      "registration" -> "653061",
      "welcome" -> "653064",
      "proposal-accepted" -> "653068",
      "proposal-refused" -> "653073",
      "forgotten-password" -> "653067",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-hu" -> Map(
      "registration" -> "653078",
      "welcome" -> "653082",
      "proposal-accepted" -> "653085",
      "proposal-refused" -> "653086",
      "forgotten-password" -> "653083",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-ie" -> Map(
      "registration" -> "652578",
      "welcome" -> "652583",
      "proposal-accepted" -> "652587",
      "proposal-refused" -> "652595",
      "forgotten-password" -> "652601",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-it" -> Map(
      "registration" -> "653091",
      "welcome" -> "653094",
      "proposal-accepted" -> "653100",
      "proposal-refused" -> "653102",
      "forgotten-password" -> "653098",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-lt" -> Map(
      "registration" -> "653105",
      "welcome" -> "653115",
      "proposal-accepted" -> "653122",
      "proposal-refused" -> "653123",
      "forgotten-password" -> "653118",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-lu" -> Map(
      "registration" -> "653127",
      "welcome" -> "653131",
      "proposal-accepted" -> "653133",
      "proposal-refused" -> "653136",
      "forgotten-password" -> "653132",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-lv" -> Map(
      "registration" -> "654304",
      "welcome" -> "654305",
      "proposal-accepted" -> "654309",
      "proposal-refused" -> "654311",
      "forgotten-password" -> "654308",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-mt" -> Map(
      "registration" -> "654315",
      "welcome" -> "654323",
      "proposal-accepted" -> "654324",
      "proposal-refused" -> "654327",
      "forgotten-password" -> "654322",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-nl" -> Map(
      "registration" -> "654334",
      "welcome" -> "654343",
      "proposal-accepted" -> "654353",
      "proposal-refused" -> "654355",
      "forgotten-password" -> "654345",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-pl" -> Map(
      "registration" -> "654356",
      "welcome" -> "654357",
      "proposal-accepted" -> "654360",
      "proposal-refused" -> "654362",
      "forgotten-password" -> "654359",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-pt" -> Map(
      "registration" -> "654365",
      "welcome" -> "654368",
      "proposal-accepted" -> "654372",
      "proposal-refused" -> "654377",
      "forgotten-password" -> "654369",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-ro" -> Map(
      "registration" -> "654378",
      "welcome" -> "654380",
      "proposal-accepted" -> "654385",
      "proposal-refused" -> "654388",
      "forgotten-password" -> "654382",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-se" -> Map(
      "registration" -> "654392",
      "welcome" -> "654395",
      "proposal-accepted" -> "654400",
      "proposal-refused" -> "654404",
      "forgotten-password" -> "654399",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-si" -> Map(
      "registration" -> "654408",
      "welcome" -> "654409",
      "proposal-accepted" -> "654416",
      "proposal-refused" -> "654420",
      "forgotten-password" -> "654412",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weeuropeans-sk" -> Map(
      "registration" -> "654424",
      "welcome" -> "654427",
      "proposal-accepted" -> "654432",
      "proposal-refused" -> "654436",
      "forgotten-password" -> "654429",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-at" -> Map(
      "registration" -> "652661",
      "welcome" -> "652664",
      "proposal-accepted" -> "652716",
      "proposal-refused" -> "652721",
      "forgotten-password" -> "652667",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-be-fr" -> Map(
      "registration" -> "652757",
      "welcome" -> "652761",
      "proposal-accepted" -> "652767",
      "proposal-refused" -> "652768",
      "forgotten-password" -> "652763",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-be-nl" -> Map(
      "registration" -> "652773",
      "welcome" -> "652775",
      "proposal-accepted" -> "652789",
      "proposal-refused" -> "652792",
      "forgotten-password" -> "652780",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-bg" -> Map(
      "registration" -> "652795",
      "welcome" -> "652797",
      "proposal-accepted" -> "652802",
      "proposal-refused" -> "652810",
      "forgotten-password" -> "652799",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-cy" -> Map(
      "registration" -> "652869",
      "welcome" -> "652876",
      "proposal-accepted" -> "652886",
      "proposal-refused" -> "652891",
      "forgotten-password" -> "652881",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-cz" -> Map(
      "registration" -> "652898",
      "welcome" -> "652905",
      "proposal-accepted" -> "652913",
      "proposal-refused" -> "652915",
      "forgotten-password" -> "652910",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-de" -> Map(
      "registration" -> "652921",
      "welcome" -> "652927",
      "proposal-accepted" -> "652942",
      "proposal-refused" -> "652947",
      "forgotten-password" -> "652933",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-dk" -> Map(
      "registration" -> "652956",
      "welcome" -> "652958",
      "proposal-accepted" -> "652964",
      "proposal-refused" -> "652969",
      "forgotten-password" -> "652962",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-ee" -> Map(
      "registration" -> "652974",
      "welcome" -> "652978",
      "proposal-accepted" -> "652987",
      "proposal-refused" -> "652989",
      "forgotten-password" -> "652983",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-es" -> Map(
      "registration" -> "653001",
      "welcome" -> "653004",
      "proposal-accepted" -> "653012",
      "proposal-refused" -> "653014",
      "forgotten-password" -> "653007",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-fi" -> Map(
      "registration" -> "653021",
      "welcome" -> "653028",
      "proposal-accepted" -> "653037",
      "proposal-refused" -> "653041",
      "forgotten-password" -> "653031",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-fr" -> Map(
      "registration" -> "652552",
      "welcome" -> "652573",
      "proposal-accepted" -> "222512",
      "proposal-refused" -> "708026",
      "forgotten-password" -> "191459",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-gr" -> Map(
      "registration" -> "653046",
      "welcome" -> "653049",
      "proposal-accepted" -> "653054",
      "proposal-refused" -> "653056",
      "forgotten-password" -> "653050",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-hr" -> Map(
      "registration" -> "653061",
      "welcome" -> "653064",
      "proposal-accepted" -> "653068",
      "proposal-refused" -> "653073",
      "forgotten-password" -> "653067",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-hu" -> Map(
      "registration" -> "653078",
      "welcome" -> "653082",
      "proposal-accepted" -> "653085",
      "proposal-refused" -> "653086",
      "forgotten-password" -> "653083",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-ie" -> Map(
      "registration" -> "652578",
      "welcome" -> "652583",
      "proposal-accepted" -> "652587",
      "proposal-refused" -> "652595",
      "forgotten-password" -> "652601",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-it" -> Map(
      "registration" -> "653091",
      "welcome" -> "653094",
      "proposal-accepted" -> "653100",
      "proposal-refused" -> "653102",
      "forgotten-password" -> "653098",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-lt" -> Map(
      "registration" -> "653105",
      "welcome" -> "653115",
      "proposal-accepted" -> "653122",
      "proposal-refused" -> "653123",
      "forgotten-password" -> "653118",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-lu" -> Map(
      "registration" -> "653127",
      "welcome" -> "653131",
      "proposal-accepted" -> "653133",
      "proposal-refused" -> "653136",
      "forgotten-password" -> "653132",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-lv" -> Map(
      "registration" -> "654304",
      "welcome" -> "654305",
      "proposal-accepted" -> "654309",
      "proposal-refused" -> "654311",
      "forgotten-password" -> "654308",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-mt" -> Map(
      "registration" -> "654315",
      "welcome" -> "654323",
      "proposal-accepted" -> "654324",
      "proposal-refused" -> "654327",
      "forgotten-password" -> "654322",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-nl" -> Map(
      "registration" -> "654334",
      "welcome" -> "654343",
      "proposal-accepted" -> "654353",
      "proposal-refused" -> "654355",
      "forgotten-password" -> "654345",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-pl" -> Map(
      "registration" -> "654356",
      "welcome" -> "654357",
      "proposal-accepted" -> "654360",
      "proposal-refused" -> "654362",
      "forgotten-password" -> "654359",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-pt" -> Map(
      "registration" -> "654365",
      "welcome" -> "654368",
      "proposal-accepted" -> "654372",
      "proposal-refused" -> "654377",
      "forgotten-password" -> "654369",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-ro" -> Map(
      "registration" -> "654378",
      "welcome" -> "654380",
      "proposal-accepted" -> "654385",
      "proposal-refused" -> "654388",
      "forgotten-password" -> "654382",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-se" -> Map(
      "registration" -> "654392",
      "welcome" -> "654395",
      "proposal-accepted" -> "654400",
      "proposal-refused" -> "654404",
      "forgotten-password" -> "654399",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-si" -> Map(
      "registration" -> "654408",
      "welcome" -> "654409",
      "proposal-accepted" -> "654416",
      "proposal-refused" -> "654420",
      "forgotten-password" -> "654412",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    ),
    "weuropeanround-sk" -> Map(
      "registration" -> "654424",
      "welcome" -> "654427",
      "proposal-accepted" -> "654432",
      "proposal-refused" -> "654436",
      "forgotten-password" -> "654429",
      "acceptedOrganisation" -> "393225",
      "refusedOrganisation" -> "393224",
      "forgotPasswordOrganisation" -> "618004"
    )
  )

}
