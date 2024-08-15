package cotoami.components

import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.geomap.maplibre
import cotoami.geomap.pmtiles

@react object MapLibre {

  // Enable to load PMTiles files.
  //
  // addProtocol works best if it is only called once in the lifecycle of your application.
  // https://docs.protomaps.com/pmtiles/maplibre
  val protocol = new pmtiles.Protocol()
  maplibre.addProtocol("pmtiles", protocol.tile)

  case class Props(
      id: String,
      defaultPosition: (Double, Double),
      defaultZoom: Int,
      style: String
  )

  val component = FunctionalComponent[Props] { props =>
    // Initialize the map
    useEffect(
      () => {
        // Delay rendering the map to ensure it to fit to the container section.
        js.timers.setTimeout(10) {
          val map = new maplibre.Map(new maplibre.MapOptions {
            override val container = props.id
            override val zoom = props.defaultZoom
            override val center =
              js.Tuple2.fromScalaTuple2(props.defaultPosition)
            override val style = props.style
            override val transformRequest = _transformRequest
          })
        }
      },
      Seq.empty
    )

    section(id := props.id, className := "geomap")()
  }

  private val _transformRequest
      : js.Function2[String, String, maplibre.RequestParameters] =
    (url: String, resourceType: String) => {
      val absoluteUrl =
        if (resourceType == "Source")
          if (url.endsWith(".pmtiles"))
            "pmtiles://" + toAbsoluteUrl(url)
          else
            toAbsoluteUrl(url)
        else if (resourceType == "Tile")
          url
        else
          toAbsoluteUrl(url)

      new maplibre.RequestParameters {
        val url = absoluteUrl
      }
    }

  private val UrlRegex = "^([a-z][a-z0-9+\\-.]*):".r

  private def toAbsoluteUrl(url: String): String =
    if (UrlRegex.matches(url))
      url
    else
      dom.window.location.origin + url
}
