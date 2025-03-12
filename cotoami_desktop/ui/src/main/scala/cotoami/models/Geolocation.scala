package cotoami.models

import scala.util.{Failure, Success}
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import cats.effect.IO

import fui.Cmd
import cotoami.libs.geomap.maplibre.LngLat
import cotoami.libs.exifr

case class Geolocation(longitude: Double, latitude: Double) {
  val lng = longitude
  val lat = latitude

  def toLngLat: (Double, Double) = (longitude, latitude)

  def toMapLibre: LngLat = new LngLat(longitude, latitude)
}

object Geolocation {
  val default: Geolocation = Geolocation(0, 0)

  def fromLngLat(lngLat: (Double, Double)): Geolocation =
    Geolocation(lngLat._1, lngLat._2)

  def fromMapLibre(lngLat: LngLat): Geolocation = fromLngLat(lngLat.toArray())

  def fromExif(
      file: dom.Blob
  ): Cmd.One[Either[Throwable, Option[Geolocation]]] =
    Cmd(IO.async { cb =>
      IO {
        exifr.gps(file).onComplete {
          case Success(gps) => {
            val location = gps.toOption.map(gps =>
              Geolocation(
                longitude = gps.longitude,
                latitude = gps.latitude
              )
            )
            cb(Right(Some(Right(location))))
          }
          case Failure(t) => cb(Right(Some(Left(t))))
        }
        None // no finalizer on cancellation
      }
    })
}
