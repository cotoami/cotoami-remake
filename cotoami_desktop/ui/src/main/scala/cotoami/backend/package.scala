package cotoami

import java.time._
import scala.scalajs.js.Dynamic.{literal => jso}

import cotoami.models.{DateTimeRange, Geolocation}

package object backend {
  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")

  def geolocationJson(location: Geolocation) =
    jso(longitude = location.longitude, latitude = location.latitude)

  def dateTimeRangeJson(range: DateTimeRange) =
    jso(start = range.startUtcIso, end = range.endUtcIso.getOrElse(null))
}
