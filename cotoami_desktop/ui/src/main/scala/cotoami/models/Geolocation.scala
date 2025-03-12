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

  case class MarkerOfCotos(
      location: Geolocation,
      cotos: Seq[Coto],
      nodeIconUrls: Set[String],
      inFocus: Boolean
  ) {
    def addCoto(
        coto: Coto,
        nodeIconUrl: String,
        inFocus: Boolean
    ): MarkerOfCotos =
      copy(
        cotos = cotos :+ coto,
        nodeIconUrls = nodeIconUrls + nodeIconUrl,
        inFocus = inFocus || this.inFocus
      )

    def containsCotonomas: Boolean = cotos.exists(_.isCotonoma)

    def label: Option[String] = cotos match {
      case Seq()     => None
      case Seq(coto) => coto.nameAsCotonoma
      case cotos =>
        cotos.flatMap(_.nameAsCotonoma) match {
          case Seq() => None
          case names => Some(names.mkString(" / "))
        }
    }
  }
}
