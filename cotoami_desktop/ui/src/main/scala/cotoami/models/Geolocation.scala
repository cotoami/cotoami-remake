package cotoami.models

import scala.util.{Failure, Success}
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import cats.effect.IO

import fui.Cmd
import cotoami.libs.geomap.maplibre.{LngLat, LngLatBounds}
import cotoami.libs.exifr

case class Geolocation(longitude: Double, latitude: Double) {
  val lng = longitude
  val lat = latitude

  def toLngLat: (Double, Double) = (this.longitude, this.latitude)

  def toMapLibre: LngLat = new LngLat(this.longitude, this.latitude)
}

object Geolocation {
  // The Tokyo station
  val default: Geolocation = Geolocation(139.76730676352, 35.680959106959)

  def fromLngLat(lngLat: (Double, Double)): Geolocation =
    Geolocation(lngLat._1, lngLat._2)

  def fromMapLibre(lngLat: LngLat): Geolocation = fromLngLat(lngLat.toArray())

  def detect(file: dom.Blob): Cmd[Either[Throwable, Geolocation]] =
    Cmd(IO.async { cb =>
      IO {
        exifr.gps(file).onComplete {
          case Success(gps) =>
            gps.toOption match {
              case Some(gps) => {
                val location = Geolocation(
                  longitude = gps.longitude,
                  latitude = gps.latitude
                )
                cb(Right(Some(Right(location))))
              }
              case None => cb(Right(None))
            }
          case Failure(t) => cb(Right(Some(Left(t))))
        }
        None // no finalizer on cancellation
      }
    })
}

case class GeoBounds(southwest: Geolocation, northeast: Geolocation) {
  val sw = southwest
  val ne = northeast

  def contains(location: Geolocation): Boolean = {
    val containsLatitude =
      this.sw.lat <= location.lat && location.lat <= this.ne.lat
    val containsLongitude =
      if (this.sw.lng <= this.ne.lng)
        this.sw.lng <= location.lng && location.lng <= this.ne.lng
      else // wrapped coordinates
        this.sw.lng >= location.lng && location.lng >= this.ne.lng

    containsLatitude && containsLongitude
  }

  def toMapLibre: LngLatBounds =
    new LngLatBounds(southwest.toMapLibre, northeast.toMapLibre)
}

object GeoBounds {
  def fromLngLat(sw: (Double, Double), ne: (Double, Double)): GeoBounds =
    GeoBounds(Geolocation.fromLngLat(sw), Geolocation.fromLngLat(ne))

  def fromMapLibre(bounds: LngLatBounds): GeoBounds =
    fromLngLat(bounds.getSouthWest().toArray(), bounds.getNorthEast().toArray())
}
