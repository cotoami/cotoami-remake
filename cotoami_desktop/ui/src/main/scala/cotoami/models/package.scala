package cotoami

import java.time.Instant

package object models {

  case class Id[T](uuid: String) extends AnyVal

  trait Entity[T] {
    def id: Id[T]

    override def hashCode(): Int = id.hashCode()

    // This default implementation should be overridden in subclasses
    override def equals(that: Any): Boolean =
      that match {
        case that: Entity[_] => id == that.id
        case _               => false
      }
  }

  type CenterOrBounds = Either[Geolocation, GeoBounds]

  def parseUtcIso(s: String): Instant = Instant.parse(s"${s}Z")
}
