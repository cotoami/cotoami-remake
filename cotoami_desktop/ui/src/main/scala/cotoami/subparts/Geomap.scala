package cotoami.subparts

import scala.scalajs.js

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.libs.tauri
import cotoami.libs.geomap.{maplibre, pmtiles}
import cotoami.models.Geolocation

@react object Geomap {

  // Enable to load PMTiles files.
  //
  // addProtocol works best if it is only called once in the lifecycle of your application.
  // https://docs.protomaps.com/pmtiles/maplibre
  val protocol = new pmtiles.Protocol()
  maplibre.addProtocol("pmtiles", protocol.tile)

  case class Props(
      id: String,
      defaultPosition: Geolocation = Geolocation.default,
      defaultZoom: Int,
      style: String,
      resourceDir: Option[String] = None
  ) {
    def styleUrl: String = toAbsoluteUrl(this.style)

    def toAbsoluteUrl(url: String): String =
      if (isUrl(url))
        url
      else {
        // Can't use Tauri's `resolveResource` here since it returns Promise/Future,
        // which doesn't suit to maplibre.MapOptions.transformRequest.
        // Instead, we manually join the `resourceDir` from `SystemInfo` and
        // the given path with its separators replaced with the platform-specific ones.
        // https://github.com/tauri-apps/tauri/issues/8599#issuecomment-1890982596
        val path =
          this.resourceDir.getOrElse("") + url.replace("/", tauri.path.sep)
        tauri.convertFileSrc(path)
      }
  }

  val component = FunctionalComponent[Props] { props =>
    val _transformRequest
        : js.Function2[String, String, maplibre.RequestParameters] =
      (url: String, resourceType: String) => {
        val absoluteUrl =
          if (resourceType == "Source")
            if (url.endsWith(".pmtiles"))
              "pmtiles://" + props.toAbsoluteUrl(url)
            else
              props.toAbsoluteUrl(url)
          else if (resourceType == "Tile")
            url
          else
            props.toAbsoluteUrl(url)

        new maplibre.RequestParameters {
          val url = absoluteUrl
        }
      }

    // Initialize the map
    useEffect(
      () => {
        // Delay rendering the map to ensure it to fit to the container section.
        js.timers.setTimeout(10) {
          val map = new maplibre.Map(new maplibre.MapOptions {
            override val container = props.id
            override val zoom = props.defaultZoom
            override val center = toLngLat(props.defaultPosition)
            override val style = props.styleUrl
            override val transformRequest = _transformRequest
          })
          map.addControl(new maplibre.NavigationControl())
        }
      },
      Seq.empty
    )

    section(id := props.id, className := "geomap")()
  }

  private val UrlRegex = "^([a-z][a-z0-9+\\-.]*):".r

  private def isUrl(string: String): Boolean =
    UrlRegex.findFirstIn(string).isDefined

  private def toLngLat(location: Geolocation): js.Tuple2[Double, Double] =
    js.Tuple2(location.longitude, location.latitude)
}
