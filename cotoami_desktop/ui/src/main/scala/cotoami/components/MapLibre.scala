package cotoami.components

import scala.util.{Failure, Success}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.libs.tauri
import cotoami.libs.geomap.{maplibre, pmtiles}

@react object MapLibre {

  // Enable to load PMTiles files.
  //
  // addProtocol works best if it is only called once in the lifecycle of your application.
  // https://docs.protomaps.com/pmtiles/maplibre
  val protocol = new pmtiles.Protocol()
  maplibre.addProtocol("pmtiles", protocol.tile)

  case class Props(
      id: String,
      center: (Double, Double), // LngLat
      zoom: Int,
      focusedLocation: Option[(Double, Double)] = None, // LngLat
      styleLocation: String = "/geomap/style.json",
      vectorTilesLocation: String = "/geomap/japan.pmtiles"
  )

  val component = FunctionalComponent[Props] { props =>
    val resourceDirRef = useRef("")
    val mapRef = useRef[maplibre.Map](null)

    // Resolve a path as an absolute URL.
    //
    // If the given location is a local file path, this function converts it
    // into an absolute URL of Tauri's asset protocol, otherwise it returns the
    // location as is.
    val toAbsoluteUrl: js.Function1[String, String] = useCallback(
      (location: String) =>
        if (isUrl(location))
          location
        else {
          // Can't use Tauri's `resolveResource` here since it returns Promise/Future,
          // which doesn't suit to the synchronous `maplibre.MapOptions.transformRequest`.
          // Instead, we manually join the `resourceDir` and the given path with
          // its separators replaced with the platform-specific ones.
          // https://github.com/tauri-apps/tauri/issues/8599#issuecomment-1890982596
          val path =
            resourceDirRef.current + location.replace("/", tauri.path.sep)
          tauri.convertFileSrc(path)
        },
      Seq.empty
    )

    // Resolve a path as a vector tiles URL.
    val toVectorTilesUrl: js.Function1[String, String] = useCallback(
      (location: String) =>
        if (
          !location.startsWith(PMTilesUrlPrefix) &&
          location.endsWith(".pmtiles")
        )
          PMTilesUrlPrefix + toAbsoluteUrl(location)
        else
          toAbsoluteUrl(location),
      Seq.empty
    )

    val _transformRequest
        : js.Function2[String, String, maplibre.RequestParameters] =
      useCallback(
        (location: String, resourceType: String) => {
          val absoluteUrl =
            if (resourceType == "Source")
              if (location == VectorTilesUrlPlaceHolder)
                toVectorTilesUrl(props.vectorTilesLocation)
              else
                toVectorTilesUrl(location)
            else if (resourceType == "Tile")
              location
            else
              toAbsoluteUrl(location)

          new maplibre.RequestParameters {
            val url = absoluteUrl
          }
        },
        Seq.empty
      )

    // Initialize the map
    useEffect(
      () => {
        tauri.path.resourceDir().onComplete {
          case Success(dir) => {
            // The tauri resource dir where local map resources are located.
            resourceDirRef.current = dir

            // Delay rendering the map to ensure it to fit to the container section.
            js.timers.setTimeout(10) {
              val map = new maplibre.Map(new maplibre.MapOptions {
                override val container = props.id
                override val zoom = props.zoom
                override val center = js.Tuple2.fromScalaTuple2(props.center)
                override val style = toAbsoluteUrl(props.styleLocation)
                override val transformRequest = _transformRequest
              })
              map.addControl(new maplibre.NavigationControl())
              mapRef.current = map
            }
          }
          case Failure(t) =>
            println(s"Couldn't get tauri.path.resourceDir: ${t.toString()}")
        }
      },
      Seq.empty
    )

    section(id := props.id, className := "geomap")()
  }

  private val UrlRegex = "^([a-z][a-z0-9+\\-.]*):".r
  private val PMTilesUrlPrefix = "pmtiles://"
  private val VectorTilesUrlPlaceHolder = "$mainVectorTilesUrl"

  private def isUrl(string: String): Boolean =
    UrlRegex.findFirstIn(string).isDefined
}
