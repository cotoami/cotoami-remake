package cotoami

import java.time._

package object backend {

  case class Id[T](uuid: String) extends AnyVal

  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")
}
