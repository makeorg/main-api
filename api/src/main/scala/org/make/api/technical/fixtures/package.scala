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

package org.make.api.technical

import org.scalacheck.Gen
import org.scalacheck.Gen.Parameters
import org.scalacheck.rng.Seed

package object fixtures {

  implicit class RichGen[T](val self: Gen[T]) extends AnyVal {
    def value: T = {
      self.pureApply(Parameters.default, Seed.random())
    }
  }
}

object GenReadableString {
  val vowels = Seq("a", "e", "i", "o", "u")
  val consonants =
    Seq("b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "r", "s", "t", "v", "w", "x", "y", "z")
  def lowerVowel: Gen[String] = Gen.oneOf(vowels)
  def upperVowel: Gen[String] = Gen.oneOf(vowels.map(_.toUpperCase))
  def lowerConsonants: Gen[String] = Gen.oneOf(consonants)
  def upperConsonants: Gen[String] = Gen.oneOf(consonants.map(_.toUpperCase))
  val vowelsStr: Gen[String] =
    Gen.frequency((1, lowerVowel), (1, upperVowel))
  val consonantsStr: Gen[String] =
    Gen.frequency((1, lowerConsonants), (1, upperConsonants))

  def readable(n: Int): Gen[String] =
    Gen
      .sequence((1 until n).map(x => if (x % 2 == 0) consonantsStr else vowelsStr))
      .map(x => x.toArray.mkString)

}
