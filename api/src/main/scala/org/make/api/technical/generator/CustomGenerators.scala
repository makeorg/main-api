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

package org.make.api.technical.generator

import java.time.temporal.ChronoUnit
import java.time.{ZoneId, ZonedDateTime}

import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import org.make.core.DateHelper._
import org.make.core.SlugHelper
import org.scalacheck.Gen

import scala.io.Source

object CustomGenerators {
  object Color {
    def gen: Gen[String] = Gen.listOfN(6, Gen.hexChar).map(_.mkString.toLowerCase.prependedAll("#"))
  }

  object LoremIpsumGen {
    private lazy val loremIpsumWords: Seq[String] = Source.fromResource("loremIpsum.csv").getLines().toSeq

    def word: Gen[String] = Gen.oneOf(loremIpsumWords)
    def words: Gen[Seq[String]] = Gen.someOf(loremIpsumWords).map(_.toSeq)
    def words(n: Int): Gen[Seq[String]] = Gen.listOfN(n, word).map(_.toSeq)

    def sentence(maxLength: Option[PosInt] = None): Gen[String] =
      Gen.atLeastOne(loremIpsumWords).map(_.toSeq).map { sen =>
        val ret = sen.mkString("", " ", ".").capitalize
        maxLength.collect {
          case length if length < ret.length => ret.take(ret.take(length).lastIndexOf(" ")).appendedAll(".")
        }.getOrElse(ret)
      }
    def sentences(n: Int, maxLength: Option[PosInt] = None): Gen[List[String]] = Gen.listOfN(n, sentence(maxLength))

    def slug(maxLength: Option[PosInt] = None): Gen[String] = sentence(maxLength).map(SlugHelper.apply)
  }

  object ImageUrl {
    def gen(width: Int, height: Int): Gen[String] =
      Gen.chooseNum(0, 1084).map(picNum => s"https://i.picsum.photos/id/$picNum/$width/$height.jpg")
  }

  object Mail {
    def gen: Gen[String] = Gen.uuid.map(id => s"yopmail+$id@make.org")
  }

  object PostalCode {
    def gen: Gen[String] = Gen.listOfN(5, Gen.numChar).map(_.mkString)
  }

  object Time {
    def zonedDateTime: Gen[ZonedDateTime] =
      Gen.calendar.map(
        calendar =>
          ZonedDateTime.ofInstant(calendar.toInstant, ZoneId.systemDefault()).toUTC.truncatedTo(ChronoUnit.MILLIS)
      )
  }
}
