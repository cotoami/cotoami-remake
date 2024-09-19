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
      nodeIconUrls: Set[String],
      inFocus: Boolean
  ) {
    def addCoto(
        coto: Coto,
        nodeIconUrl: String,
        inFocus: Boolean
    ): MarkerOfCotos =
      this.copy(
        cotos = this.cotos :+ coto,
        nodeIconUrls = this.nodeIconUrls + nodeIconUrl,
        inFocus = inFocus || this.inFocus
      )

    def containsCotonomas: Boolean = this.cotos.exists(_.isCotonoma)

    def label: Option[String] = this.cotos match {
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
