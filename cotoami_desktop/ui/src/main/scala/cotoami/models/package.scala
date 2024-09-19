package cotoami

import java.time.Instant

package object models {

  case class Id[T](uuid: String) extends AnyVal

  trait Entity[T] {
    def id: Id[T]
  }

  def parseUtcIso(s: String): Instant = Instant.parse(s"${s}Z")
}
