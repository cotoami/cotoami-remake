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
import cotoami.libs.geomap.maplibre
import cotoami.libs.geomap.maplibre._
import cotoami.libs.geomap.pmtiles

@react object MapLibre {

  // Enable to load PMTiles files.
  //
  // addProtocol works best if it is only called once in the lifecycle of your application.
  // https://docs.protomaps.com/pmtiles/maplibre
  val protocol = new pmtiles.Protocol()
  maplibre.addProtocol("pmtiles", protocol.tile)

  case class Props(
      id: String,

      // Viewport
      center: (Double, Double), // LngLat
      zoom: Int,
      disableRotation: Boolean = true,

      // Markers
      focusedLocation: Option[(Double, Double)] = None, // LngLat

      // Changing this value forces to sync the map state with the current props
      forceSync: Int = 0,

      // Map resources
      styleLocation: String = "/geomap/style.json",
      vectorTilesLocation: String = "/geomap/japan.pmtiles",

      // Event handlers
      onInit: Option[() => Unit] = None,
      onClick: Option[MapMouseEvent => Unit] = None
  )

  val component = FunctionalComponent[Props] { props =>
    val resourceDirRef = useRef("")
    val mapRef = useRef[Option[ExtendedMap]](None)

    // To allow the callbacks to access the up-to-date props.onClick
    val onClickRef =
      useRef[Option[MapMouseEvent => Unit]](props.onClick)

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

    val _transformRequest: js.Function2[String, String, RequestParameters] =
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

          new RequestParameters {
            val url = absoluteUrl
          }
        },
        Seq.empty
      )

    // Initialize the map
    useEffect(
      () => {
        val onClick: js.Function1[MapMouseEvent, Unit] =
          e => onClickRef.current.foreach(_(e))

        tauri.path.resourceDir().onComplete {
          case Success(dir) => {
            // The tauri resource dir where local map resources are located.
            resourceDirRef.current = dir

            // Delay rendering the map to ensure it to fit to the container section.
            js.timers.setTimeout(10) {
              val map = new ExtendedMap(new MapOptions {
                override val container = props.id
                override val zoom = props.zoom
                override val center = js.Tuple2.fromScalaTuple2(props.center)
                override val style = toAbsoluteUrl(props.styleLocation)
                override val transformRequest = _transformRequest
              })
              map.addControl(
                new NavigationControl(
                  new NavigationControlOptions() {
                    override val showCompass = !props.disableRotation
                  }
                )
              )

              // Disable map rotation
              if (props.disableRotation) {
                map.disableRotation()
              }

              // Restore the state
              props.focusedLocation.map(map.focusLocation)

              // Event handlers
              map.on("click", onClick)
              props.onInit.map(_())

              mapRef.current = Some(map)
            }
          }
          case Failure(t) =>
            println(s"Couldn't get tauri.path.resourceDir: ${t.toString()}")
        }

        () =>
          mapRef.current.foreach { map =>
            map.off("click", onClick)
          }
      },
      Seq.empty
    )

    // On/Off a focused marker.
    useEffect(
      () =>
        (mapRef.current, props.focusedLocation) match {
          case (Some(map), Some(location)) => map.focusLocation(location)
          case (Some(map), None)           => map.unfocusLocation()
          case _                           => ()
        },
      Seq(props.forceSync, props.focusedLocation.toString())
    )

    // Change the map center and zoom with an animated transition.
    useEffect(
      () =>
        mapRef.current.foreach(
          _.easeTo(new EaseToOptions {
            override val center = js.Tuple2.fromScalaTuple2(props.center)
            override val zoom = props.zoom
            override val duration = 1000
          })
        ),
      Seq(props.forceSync, props.center.toString(), props.zoom)
    )

    // Update onClickRef
    useEffect(
      () => { onClickRef.current = props.onClick },
      Seq(props.onClick)
    )

    section(id := props.id, className := "geomap")()
  }

  private val UrlRegex = "^([a-z][a-z0-9+\\-.]*):".r
  private val PMTilesUrlPrefix = "pmtiles://"
  private val VectorTilesUrlPlaceHolder = "$mainVectorTilesUrl"

  private def isUrl(string: String): Boolean =
    UrlRegex.findFirstIn(string).isDefined

  class ExtendedMap(options: MapOptions) extends Map(options) {
    var focusedMarker: Option[Marker] = None

    def disableRotation(): Unit = {
      this.dragRotate.disable()
      this.keyboard.disable()
      this.touchZoomRotate.disableRotation()
    }

    def focusLocation(lngLat: (Double, Double)): Unit = {
      unfocusLocation()
      val marker = new Marker()
        .setLngLat(js.Tuple2.fromScalaTuple2(lngLat))
        .addTo(this)
      this.focusedMarker = Some(marker)
    }

    def unfocusLocation(): Unit = {
      this.focusedMarker.foreach(_.remove())
    }
  }
}
