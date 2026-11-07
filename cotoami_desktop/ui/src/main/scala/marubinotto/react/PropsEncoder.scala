package marubinotto.react

import scala.scalajs.js
import scala.deriving.Mirror
import scala.compiletime.{constValue, erasedValue, error, summonFrom}

trait PropsEncoder[A] { def encode(value: A): js.Object }
trait ValueEncoder[A] { def encode(value: A): js.Any }

/** Explicit null is distinct from an omitted optional property. */
enum Nullable[+A] {
  case Value(value: A)
  case Null
}
object ValueEncoder {
  given ValueEncoder[String] = value => value
  given ValueEncoder[Boolean] = value => value
  given ValueEncoder[Int] = value => value
  given ValueEncoder[Double] = value => value
  given ValueEncoder[Float] = value => value.toDouble
  given ValueEncoder[Unit] = _ => js.undefined
  given jsValue[A <: js.Any]: ValueEncoder[A] = value => value
  given domRefValue[A <: org.scalajs.dom.Element]: ValueEncoder[DomRef[A]] =
    _.raw
  given nodeValue[A <: ReactNode]: ValueEncoder[A] = _.raw
  given productValue[A](using encoder: PropsEncoder[A]): ValueEncoder[A] =
    value => encoder.encode(value)
  given [A](using encoder: ValueEncoder[A]): ValueEncoder[Option[A]] =
    value => value.fold[js.Any](js.undefined)(encoder.encode)
  given [A](using encoder: ValueEncoder[A]): ValueEncoder[Nullable[A]] = {
    case Nullable.Null         => null
    case Nullable.Value(value) => encoder.encode(value)
  }
  given [A](using encoder: ValueEncoder[A]): ValueEncoder[Seq[A]] =
    values => js.Array(values.map(encoder.encode)*)
  given [A](using encoder: ValueEncoder[A]): ValueEncoder[js.UndefOr[A]] =
    value =>
      if (js.isUndefined(value)) js.undefined
      else encoder.encode(value.asInstanceOf[A])
  given [R](using result: ValueEncoder[R]): ValueEncoder[() => R] =
    f => (() => result.encode(f())): js.Function0[js.Any]
  given [A, R](using
      arg: CallbackArg[A],
      result: ValueEncoder[R]
  ): ValueEncoder[A => R] =
    f =>
      ((a: js.Any) => result.encode(f(arg.decode(a)))): js.Function1[
        js.Any,
        js.Any
      ]
  given [A, B, R](using
      a: CallbackArg[A],
      b: CallbackArg[B],
      result: ValueEncoder[R]
  ): ValueEncoder[(A, B) => R] =
    f =>
      (
          (x: js.Any, y: js.Any) => result.encode(f(a.decode(x), b.decode(y)))
      ): js.Function2[js.Any, js.Any, js.Any]
}

/** Only explicitly supported JS callback inputs can cross the boundary. */
trait CallbackArg[A] { def decode(value: js.Any): A }
object CallbackArg {
  given CallbackArg[String] = _.asInstanceOf[String]
  given CallbackArg[Boolean] = _.asInstanceOf[Boolean]
  given CallbackArg[Int] = _.asInstanceOf[Int]
  given CallbackArg[Double] = _.asInstanceOf[Double]
  given [A <: js.Any]: CallbackArg[A] = _.asInstanceOf[A]
}
object PropsEncoder {
  inline def derived[A](using mirror: Mirror.ProductOf[A]): PropsEncoder[A] = {
    val fields = encoders[mirror.MirroredElemTypes, mirror.MirroredElemLabels]
    productEncoder[A](fields)
  }
  private def productEncoder[A](
      fields: List[(String, Any => js.Any)]
  ): PropsEncoder[A] =
    new PropsEncoder[A] {
      def encode(value: A): js.Object = {
        val props =
          js.Dynamic.global.Object.create(null).asInstanceOf[js.Dictionary[
            js.Any
          ]]
        val product = value.asInstanceOf[Product]
        fields.zipWithIndex.foreach { case ((name, encode), index) =>
          val encoded = encode(product.productElement(index))
          if (!js.isUndefined(encoded)) props(name) = encoded
        }
        props.asInstanceOf[js.Object]
      }
    }
  private inline def encoders[Types <: Tuple, Labels <: Tuple]
      : List[(String, Any => js.Any)] =
    inline erasedValue[(Types, Labels)] match {
      case _: (EmptyTuple, EmptyTuple)       => Nil
      case _: ((t *: ts), (label *: labels)) =>
        val encoder = fieldEncoder[t, label]
        (
          constValue[label].asInstanceOf[String],
          (value: Any) => encoder.encode(value.asInstanceOf[t])
        ) :: encoders[ts, labels]
    }

  private inline def fieldEncoder[A, Label]: ValueEncoder[A] = summonFrom {
    case found: ValueEncoder[A] => found
    case _                      =>
      error(
        "Unsupported external prop: " + constValue[Label].asInstanceOf[String]
      )
  }
}
