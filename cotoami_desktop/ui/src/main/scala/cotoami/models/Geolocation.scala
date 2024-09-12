package cotoami.models

import scala.util.{Failure, Success}
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import cats.effect.IO

import fui.Cmd
import cotoami.libs.geomap.maplibre.LngLat
import cotoami.libs.exifr
import cotoami.backend.Coto

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

  case class MarkerOfCotos(
      location: Geolocation,
      cotos: Seq[Coto],
      nodeIconUrls: Set[String]
  ) {
    def containsCotonomas: Boolean = this.cotos.exists(_.isCotonoma)
  }
}
