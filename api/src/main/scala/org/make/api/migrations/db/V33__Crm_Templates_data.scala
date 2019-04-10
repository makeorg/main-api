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

import java.util.UUID

import org.flywaydb.core.api.migration._

class V33__Crm_Templates_data extends BaseJavaMigration {

  override def migrate(context: Context): Unit = {
    val connection = context.getConnection
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

    def valuesToInsertCrmTemplate(questionId: String,
                                  locale: String,
                                  registration: String,
                                  welcome: String,
                                  accepted: String,
                                  refused: String,
                                  forgotPassword: String,
                                  acceptedOrganisation: String,
                                  refusedOrganisation: String,
                                  forgotPasswordOrganisation: String): String = {
      val id: String = nextId()
      (s"('$id', '$questionId', '$locale', '$registration', '$welcome'," +
        s" '$accepted', '$refused', '$forgotPassword'," +
        s" '$acceptedOrganisation', '$refusedOrganisation'," +
        s" '$forgotPasswordOrganisation')").replace("''", "null")
    }

    val valuesToInsert = (CrmTemplateRawData.templatesIdsLocales.map {
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
    } ++ CrmTemplateRawData.templatesIdsQuestions.filter {
      case (question, _) =>
        questions.get(question).isDefined
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

    connection.commit()

  }
}

object CrmTemplateRawData {
  val templatesIdsLocales = Map(
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

  val templatesIdsQuestions = Map(
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
