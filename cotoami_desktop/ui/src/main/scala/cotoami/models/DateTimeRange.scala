package cotoami.models

import java.time.Instant

case class DateTimeRange(
    startUtcIso: String,
    endUtcIso: Option[String]
) {
  lazy val start: Instant = parseUtcIso(startUtcIso)
  lazy val end: Option[Instant] = endUtcIso.map(parseUtcIso)
}
