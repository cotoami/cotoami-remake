package cotoami

import java.time._

package object backend {
  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")
}
