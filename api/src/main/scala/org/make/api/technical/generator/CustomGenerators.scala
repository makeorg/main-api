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

import org.make.core.SlugHelper
import org.scalacheck.Gen

object CustomGenerators {
  object Color {
    def atoFChar: Gen[Char] = Gen.choose(65.toChar, 70.toChar)
    def colorChar: Gen[Char] = Gen.frequency((10, Gen.numChar), (6, atoFChar))
    def colorStr: Gen[String] = Gen.listOfN(6, colorChar).map(_.mkString.prependedAll("#"))
  }

  object LoremIpsumGen {
    private val loremIpsumWords = Seq(
      "a",
      "ac",
      "accumsan",
      "ad",
      "adipiscing",
      "aenean",
      "aliquam",
      "aliquet",
      "amet",
      "ante",
      "aptent",
      "arcu",
      "at",
      "auctor",
      "augue",
      "bibendum",
      "blandit",
      "class",
      "commodo",
      "condimentum",
      "congue",
      "consectetur",
      "consequat",
      "conubia",
      "convallis",
      "cras",
      "cubilia",
      "cum",
      "curabitur",
      "curae",
      "cursus",
      "dapibus",
      "diam",
      "dictum",
      "dictumst",
      "dignissim",
      "dis",
      "dolor",
      "donec",
      "dui",
      "duis",
      "egestas",
      "eget",
      "eleifend",
      "elementum",
      "elit",
      "enim",
      "erat",
      "eros",
      "est",
      "et",
      "etiam",
      "eu",
      "euismod",
      "facilisi",
      "facilisis",
      "fames",
      "faucibus",
      "felis",
      "fermentum",
      "feugiat",
      "fringilla",
      "fusce",
      "gravida",
      "habitant",
      "habitasse",
      "hac",
      "hendrerit",
      "himenaeos",
      "iaculis",
      "id",
      "imperdiet",
      "in",
      "inceptos",
      "integer",
      "interdum",
      "ipsum",
      "justo",
      "lacinia",
      "lacus",
      "laoreet",
      "lectus",
      "leo",
      "libero",
      "ligula",
      "litora",
      "lobortis",
      "lorem",
      "luctus",
      "maecenas",
      "magna",
      "magnis",
      "malesuada",
      "massa",
      "mattis",
      "mauris",
      "metus",
      "mi",
      "molestie",
      "mollis",
      "montes",
      "morbi",
      "mus",
      "nam",
      "nascetur",
      "natoque",
      "nec",
      "neque",
      "netus",
      "nibh",
      "nisi",
      "nisl",
      "non",
      "nostra",
      "nulla",
      "nullam",
      "nunc",
      "odio",
      "orci",
      "ornare",
      "parturient",
      "pellentesque",
      "penatibus",
      "per",
      "pharetra",
      "phasellus",
      "placerat",
      "platea",
      "porta",
      "porttitor",
      "posuere",
      "potenti",
      "praesent",
      "pretium",
      "primis",
      "proin",
      "pulvinar",
      "purus",
      "quam",
      "quis",
      "quisque",
      "rhoncus",
      "ridiculus",
      "risus",
      "rutrum",
      "sagittis",
      "sapien",
      "scelerisque",
      "sed",
      "sem",
      "semper",
      "senectus",
      "sit",
      "sociis",
      "sociosqu",
      "sodales",
      "sollicitudin",
      "suscipit",
      "suspendisse",
      "taciti",
      "tellus",
      "tempor",
      "tempus",
      "tincidunt",
      "torquent",
      "tortor",
      "tristique",
      "turpis",
      "ullamcorper",
      "ultrices",
      "ultricies",
      "urna",
      "ut",
      "varius",
      "vehicula",
      "vel",
      "velit",
      "venenatis",
      "vestibulum",
      "vitae",
      "vivamus",
      "viverra",
      "volutpat",
      "vulputate"
    )

    def word: Gen[String] = Gen.oneOf(loremIpsumWords)
    def words: Gen[Seq[String]] = Gen.someOf(loremIpsumWords).map(_.toSeq)
    def words(n: Int): Gen[Seq[String]] = Gen.listOfN(n, word).map(_.toSeq)

    def sentence(maxLength: Int = -1): Gen[String] = Gen.someOf(loremIpsumWords).map { sen =>
      val ret = sen.mkString("", " ", ".").capitalize
      if (maxLength > 0 && ret.length >= maxLength) {
        ret.take(ret.take(maxLength).lastIndexOf(" ")).appendedAll(".")
      } else {
        ret
      }
    }
    def sentences(n: Int, maxLength: Int = -1): Gen[List[String]] = Gen.listOfN(n, sentence(maxLength))

    def slug(maxLength: Int = -1): Gen[String] = sentence(maxLength).map(SlugHelper.apply)
  }

  object Image {
    def url(width: Int, height: Int): Gen[String] =
      Gen.chooseNum(0, 1084).map(picNum => s"https://i.picsum.photos/id/$picNum/$width/$height.jpg")
  }

  object Mail {
    def safe: Gen[String] = LoremIpsumGen.slug(maxLength = 50).flatMap { name =>
      GenReadableString.readableWord(20).map(tag => s"$name+$tag@e-mail.cafe")
    }
    def make: Gen[String] = LoremIpsumGen.slug(maxLength = 50).map(name => s"yopmail+$name@make.org")
  }

  @Deprecated
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

    def readableWord(n: Int): Gen[String] =
      Gen
        .sequence((1 until n).map(x => if (x % 2 == 0) consonantsStr else vowelsStr))
        .map(x => x.toArray.mkString)
  }
}
