/*
 * Copyright 2018-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package higherkindness.skeuomorph.openapi.client.http4s

import higherkindness.skeuomorph.Printer
import higherkindness.skeuomorph.Printer._
import higherkindness.skeuomorph.catz.contrib.ContravariantMonoidalSyntax._
import higherkindness.skeuomorph.catz.contrib.Decidable._
import higherkindness.skeuomorph.openapi._
import higherkindness.skeuomorph.openapi.print._
import client.http4s.print._
import cats.implicits._
import qq.droste._
import cats.implicits._

import higherkindness.skeuomorph.openapi.print.Codecs

package object circe {
  private val http4sPackages =
    List("org.http4s.{EntityEncoder, EntityDecoder}", "org.http4s.circe._", "cats.Applicative", "cats.effect.Sync").map(
      PackageName.apply)

  private val packages =
    List("io.circe._", "io.circe.generic.semiauto._").map(PackageName.apply) ++ http4sPackages

  private def codecsTypes[T](name: String): ((String, Tpe[T]), (String, Tpe[T])) = {
    val tpe = Tpe[T](name)
    (name -> tpe) -> (s"Option${name}" -> tpe.copy(required = false))
  }

  implicit def circeCodecsPrinter[T: Basis[JsonSchemaF, ?]]: Printer[Codecs] =
    (
      sepBy(space *< space *< importDef, "\n") >* newLine,
      optional(space *< space *< circeEncoder[T] >* newLine) >|< (space *< space *< enumCirceEncoder),
      optional(space *< space *< circeDecoder[T] >* newLine),
      space *< space *< entityEncoder[T] >* newLine,
      space *< space *< entityEncoder[T] >* newLine,
      space *< space *< entityDecoder[T] >* newLine)
      .contramapN {
        case CaseClassCodecs(name) =>
          val (default, optionType) = codecsTypes[T](name)
          (packages, default.some.asLeft, default.some, default, optionType, default)
        case ListCodecs(name) =>
          val (default, optionType) = codecsTypes[T](name)
          (http4sPackages, none.asLeft, none, default, optionType, default)
        case EnumCodecs(name, values) =>
          val (default, optionType) = codecsTypes[T](name)
          (packages, (name, values).asRight, none, default, optionType, default)
      }

  implicit def http4sCodecsPrinter[T: Basis[JsonSchemaF, ?]]: Printer[EntityCodecs[T]] =
    (
      sepBy(space *< space *< importDef, "\n") >* newLine,
      space *< space *< entityEncoder[T] >* newLine,
      space *< space *< entityDecoder[T] >* newLine)
      .contramapN { x =>
        val y = x.name -> Tpe[T](x.name)
        (packages, y, y)
      }

  private def decoderDef[T: Basis[JsonSchemaF, ?], B](body: Printer[B]): Printer[(String, Tpe[T], B)] =
    (konst("implicit val ") *< string >* konst("Decoder: "), konst("Decoder[") *< tpe[T] >* konst("] = "), body)
      .contramapN(identity)

  def enumCirceEncoder[T: Basis[JsonSchemaF, ?], B]: Printer[(String, List[String])] =
    decoderDef[T, (String, List[(String)])]((
      konst("Decoder.decodeString.emap {") *< newLine *<
        sepBy[(String, String)](
          (space *< space *< konst("case \"") *< string >* konst("\" => "), string >* konst(".asRight"))
            .contramapN(identity),
          "\n"
        ) >* newLine,
      konst("""  case x => s"$x is not valid """) *< string >* konst("""".asLeft""") *< newLine *< konst("}") *< newLine
    ).contramapN(x => flip(second(x)(_.map(x => x -> x))))).contramap { case (x, xs) => (x, Tpe[T](x), x -> xs) }

  def circeDecoder[T: Basis[JsonSchemaF, ?]]: Printer[(String, Tpe[T])] =
    decoderDef(konst("deriveDecoder[") *< tpe[T] >* konst("]"))
      .contramap(x => (x._1, x._2, x._2))

  def circeEncoder[T: Basis[JsonSchemaF, ?]]: Printer[(String, Tpe[T])] =
    (
      konst("implicit val ") *< string >* konst("Encoder: "),
      konst("Encoder[") *< tpe[T] >* konst("] = "),
      konst("deriveEncoder[") *< tpe[T] >* konst("]"))
      .contramapN(x => (x._1, x._2, x._2))

  def entityDecoder[T: Basis[JsonSchemaF, ?]]: Printer[(String, Tpe[T])] =
    (
      konst("implicit def ") *< string >* konst("EntityDecoder[F[_]:Sync]: "),
      konst("EntityDecoder[F, ") *< tpe[T] >* konst("] = "),
      konst("jsonOf[F, ") *< tpe[T] >* konst("]"))
      .contramapN(x => (x._1, x._2, x._2))

  def entityEncoder[T: Basis[JsonSchemaF, ?]]: Printer[(String, Tpe[T])] =
    (
      konst("implicit def ") *< string >* konst("EntityEncoder[F[_]:Applicative]: "),
      konst("EntityEncoder[F, ") *< tpe[T] >* konst("] = "),
      konst("jsonEncoderOf[F, ") *< tpe[T] >* konst("]"))
      .contramapN(x => (x._1, x._2, x._2))

}
