package cotoami

import scala.scalajs.js.|
import java.time._

package object backend {

  case class Id[T](uuid: String) extends AnyVal

  trait Entity[T] {
    def id: Id[T]
  }

  type Nullable[T] = T | Null

  object Nullable {
    def unwrap[T](value: Nullable[T]): T = value.asInstanceOf[T]

    def toOption[T](value: Nullable[T]): Option[T] =
      Option(value.asInstanceOf[T])
  }

  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")
}
