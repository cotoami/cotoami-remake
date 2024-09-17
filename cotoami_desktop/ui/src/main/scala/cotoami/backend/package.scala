package cotoami

import scala.scalajs.js.|
import java.time._

package object backend {

  type Nullable[T] = T | Null

  object Nullable {
    def unwrap[T](value: Nullable[T]): T = value.asInstanceOf[T]

    def toOption[T](value: Nullable[T]): Option[T] =
      Option(value.asInstanceOf[T])
  }

  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")
}
