package marubinotto.router

import scala.annotation.unused
import scala.scalajs.js.URIUtils

abstract class RoutePattern[A] { self =>

  protected def consume(segments: List[String]): Option[(A, List[String])]
  protected def renderSegments(value: A): List[String]

  final def /(segment: String): RoutePattern[A] =
    new RoutePattern[A] {
      override protected def consume(
          segments: List[String]
      ): Option[(A, List[String])] =
        self.consume(segments).flatMap {
          case (value, head :: tail) if decodeSegment(head) == segment =>
            Some((value, tail))
          case _ =>
            None
        }

      override protected def renderSegments(value: A): List[String] =
        self.renderSegments(value) :+ segment
    }

  final def /[B, Out](@unused _arg: Arg[B])(using
      codec: Codec[B],
      appender: Appender[A, B, Out]
  ): RoutePattern[Out] =
    new RoutePattern[Out] {
      override protected def consume(
          segments: List[String]
      ): Option[(Out, List[String])] =
        self.consume(segments).flatMap {
          case (left, head :: tail) =>
            codec
              .decode(Some(decodeSegment(head)))
              .map(value => (appender.append(left, value), tail))
          case (left, Nil) =>
            codec.decode(None).map(value => (appender.append(left, value), Nil))
        }

      override protected def renderSegments(value: Out): List[String] = {
        val (left, right) = appender.split(value)
        val encoded = codec.encode(right).getOrElse {
          throw new IllegalArgumentException(
            s"Could not encode route argument: ${right}"
          )
        }
        self.renderSegments(left) :+ encoded
      }
    }

  final def url(value: A): String = {
    val segments = renderSegments(value).map(encodeSegment)
    if (segments.isEmpty) "/"
    else "/" + segments.mkString("/")
  }

  final def unapply(path: String): Option[A] =
    consume(RoutePattern.splitPath(path)).collect {
      case (value, Nil) => value
    }

  protected final def encodeSegment(segment: String): String =
    URIUtils.encodeURIComponent(segment)

  protected final def decodeSegment(segment: String): String =
    URIUtils.decodeURIComponent(segment)
}

object Root extends RoutePattern[Unit] {
  override protected def consume(
      segments: List[String]
  ): Option[(Unit, List[String])] =
    Some(((), segments))

  override protected def renderSegments(value: Unit): List[String] = Nil
}

private object RoutePattern {
  def splitPath(path: String): List[String] =
    path
      .split('/')
      .toList
      .filter(_.nonEmpty)
}
