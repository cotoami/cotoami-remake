package cotoami

import java.time._
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import cats.effect.IO
import cats.syntax.all.*
import scala.scalajs.js.Dynamic.{literal => jso}

import cotoami.models.{DateTimeRange, Geolocation}

package object backend {

  def decodeArray[A, B](values: js.Array[A])(
      decode: A => IO[B]
  ): IO[js.Array[B]] =
    values.toList.traverse(decode).map(_.toJSArray)

  def parseJsonDateTime(s: String): Instant = Instant.parse(s"${s}Z")

  def geolocationJson(location: Geolocation) =
    jso(longitude = location.longitude, latitude = location.latitude)

  def dateTimeRangeJson(range: DateTimeRange) =
    jso(start = range.startUtcIso, end = range.endUtcIso.getOrElse(null))
}
