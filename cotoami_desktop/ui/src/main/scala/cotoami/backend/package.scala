package cotoami

import java.time._

package object backend {

  case class Id[T](uuid: String) extends AnyVal

  trait Entity[T] {
    def id: Id[T]
  }

  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")
}
