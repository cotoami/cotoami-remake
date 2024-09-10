package cotoami.components

import scala.collection.mutable.{Map => MutableMap}
import scala.util.{Failure, Success}
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._

import org.scalajs.dom
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
      disableRotation: Boolean = true,

      // Center/Zoom
      center: LngLat,
      zoom: Double,
      detectZoomClass: Option[Double => Option[String]] = None,

      // Markers
      focusedLocation: Option[LngLat] = None,
      markerDefs: Seq[MarkerDef] = Seq.empty,
      focusedMarkerId: Option[String] = None,

      // GeoBounds
      bounds: Option[LngLatBounds] = None,
      paddingToBounds: Double = 30,

      // Triggers to invoke effects
      // Changing the following values will trigger an effect.
      applyCenterZoom: Int = 0,
      addOrRemoveMarkers: Int = 0,
      refreshMarkers: Int = 0,
      fitBounds: Int = 0,

      // Map resources
      styleLocation: String = "/geomap/style.json",
      vectorTilesLocation: String = "/geomap/japan.pmtiles",

      // Event handlers (which will be registered during map or marker initialization)
      onInit: Option[LngLatBounds => Unit] = None,
      onClick: Option[MapMouseEvent => Unit] = None,
      onZoomChanged: Option[Double => Unit] = None,
      onCenterMoved: Option[LngLat => Unit] = None,
      onBoundsChanged: Option[LngLatBounds => Unit] = None,
      onMarkerClick: Option[String => Unit] = None
  )

  val component = FunctionalComponent[Props] { props =>
    val (zoomClass, setZoomClass) = useState[Option[String]](None)

    val resourceDirRef = useRef("")
    val mapRef = useRef[Option[ExtendedMap]](None)

    // To track the map state to detect change
    val boundsRef = useRef[Option[LngLatBounds]](None)

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

    // Initialize the map.
    useEffect(
      () => {
        val onClick: js.Function1[MapMouseEvent, Unit] =
          e => props.onClick.foreach(_(e))

        val detectBoundsChange = (e: MapLibreEvent) => {
          props.onBoundsChanged match {
            case Some(onBoundsChanged) => {
              val bounds = e.target.getBounds()
              if (Some(bounds).toString() != boundsRef.current.toString()) {
                boundsRef.current = Some(bounds)
                onBoundsChanged(bounds)
              }
            }
            case None => ()
          }

        }
        val onZoomend: js.Function1[MapLibreEvent, Unit] = e => {
          props.onZoomChanged.foreach(_(e.target.getZoom()))
          detectBoundsChange(e)
        }
        val onMoveend: js.Function1[MapLibreEvent, Unit] = e => {
          props.onCenterMoved.foreach(_(e.target.getCenter()))
          detectBoundsChange(e)
        }

        tauri.path.resourceDir().onComplete {
          case Success(dir) => {
            // The tauri resource dir where local map resources are located.
            resourceDirRef.current = dir

            // Delay rendering the map to ensure it to fit to the container section.
            js.timers.setTimeout(10) {
              val map = new ExtendedMap(
                new MapOptions {
                  override val container = props.id
                  override val zoom = props.zoom
                  override val center = props.center
                  override val style = toAbsoluteUrl(props.styleLocation)
                  override val transformRequest = _transformRequest
                },
                props.onMarkerClick
              )
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

              // onInit
              boundsRef.current = Some(map.getBounds())
              props.onInit.map(_(map.getBounds()))

              // Event handlers
              map.on("click", onClick)
              map.on("zoomend", onZoomend)
              map.on("moveend", onMoveend)
              map.on(
                "zoom",
                (e: MapLibreEvent) => {
                  setZoomClass(
                    props.detectZoomClass.flatMap(detect =>
                      detect(e.target.getZoom())
                    )
                  )
                }
              )

              mapRef.current = Some(map)
            }
          }
          case Failure(t) =>
            println(s"Couldn't get tauri.path.resourceDir: ${t.toString()}")
        }

        () =>
          mapRef.current.foreach { map =>
            map.off("click", onClick)
            map.off("zoomend", onZoomend)
            map.off("moveend", onMoveend)
          }
      },
      Seq.empty
    )

    // On/Off a focused location marker.
    useEffect(
      () =>
        (mapRef.current, props.focusedLocation) match {
          case (Some(map), Some(location)) => map.focusLocation(location)
          case (Some(map), None)           => map.unfocusLocation()
          case _                           => ()
        },
      Seq(props.focusedLocation.toString())
    )

    // Apply the center and zoom of the props.
    useEffect(
      () => {
        mapRef.current.foreach(
          _.flyTo(new FlyToOptions {
            override val center = props.center
            override val zoom = props.zoom
            override val duration = 2000
          })
        )
      },
      Seq(props.applyCenterZoom)
    )

    // fitBounds
    useEffect(
      () => {
        props.bounds.map(bounds =>
          mapRef.current.foreach(
            _.fitBounds(
              bounds,
              new FitBoundsOptions() {
                override val padding = props.paddingToBounds
                override val maxZoom = 18
              }
            )
          )
        )
      },
      Seq(props.fitBounds)
    )

    // Add or remove markers according to the IDs of props.markerDefs.
    useEffect(
      () => {
        mapRef.current.foreach(_.addOrRemoveMarkers(props.markerDefs))
      },
      Seq(props.addOrRemoveMarkers)
    )

    // Refresh (clear and create) markers to sync with props.markerDefs.
    useEffect(
      () => {
        mapRef.current.foreach(_.refreshMarkers(props.markerDefs))
      },
      Seq(props.refreshMarkers)
    )

    // Focus/Unfocus marker
    useEffect(
      () => {
        mapRef.current.foreach(map =>
          props.focusedMarkerId match {
            case Some(markerId) => map.focusMarker(markerId)
            case None           => map.unfocusMarker()
          }
        )
      },
      Seq(props.focusedMarkerId.toString())
    )

    div(className := s"geomap-container ${zoomClass.getOrElse("")}")(
      // For some reason, changing the `className` of the map element dynamically
      // breaks MapLibre display. So we have to put the `geomap-container` element and
      // modify its `className` instead.
      section(id := props.id, className := "geomap")()
    )
  }

  private val UrlRegex = "^([a-z][a-z0-9+\\-.]*):".r
  private val PMTilesUrlPrefix = "pmtiles://"
  private val VectorTilesUrlPlaceHolder = "$mainVectorTilesUrl"
  private val FocusedLocationMarkerClassName = "focused-location-marker"
  private val FocusedMarkerClassName = "focused-marker"

  private def isUrl(string: String): Boolean =
    UrlRegex.findFirstIn(string).isDefined

  class ExtendedMap(
      options: MapOptions,
      onMarkerClick: Option[String => Unit] = None
  ) extends Map(options) {
    var focusedLocationMarker: Option[Marker] = None
    val markers: MutableMap[String, Marker] = MutableMap.empty
    var focusedMarkerId: Option[String] = None

    def disableRotation(): Unit = {
      this.dragRotate.disable()
      this.keyboard.disable()
      this.touchZoomRotate.disableRotation()
    }

    def focusLocation(lngLat: LngLat): Unit = {
      unfocusLocation()
      val marker = new Marker()
        .setLngLat(lngLat)
        .addTo(this)
      marker.addClassName(FocusedLocationMarkerClassName)
      this.focusedLocationMarker = Some(marker)
    }

    def unfocusLocation(): Unit = {
      this.focusedLocationMarker.foreach(_.remove())
    }

    def focusMarker(markerId: String): Unit = {
      unfocusMarker()
      markers.get(markerId).foreach { marker =>
        marker.addClassName(FocusedMarkerClassName)
        this.focusedMarkerId = Some(markerId)
      }
    }

    def unfocusMarker(): Unit = {
      this.focusedMarkerId.foreach(
        markers.get(_).foreach(_.removeClassName(FocusedMarkerClassName))
      )
      this.focusedMarkerId = None
    }

    def addOrRemoveMarkers(markerDefs: Seq[MarkerDef]): Unit = {
      val defMap = markerDefs.map(d => d.id -> d).toMap

      // Add
      val toAdd = defMap.keySet.diff(this.markers.keySet)
      toAdd.flatMap(defMap.get).foreach(putMarker)

      // Remove
      val toRemove = this.markers.keySet.diff(defMap.keySet)
      toRemove.foreach(removeMarker)
    }

    def refreshMarkers(markerDefs: Seq[MarkerDef]): Unit = {
      clearMarkers()
      markerDefs.foreach(putMarker)
    }

    private def putMarker(markerDef: MarkerDef): Unit = {
      removeMarker(markerDef.id)
      val marker = new Marker(new MarkerOptions() {
        override val element = markerDef.html
        override val className = markerDef.className match {
          case Some(className) => className
          case None            => ()
        }
      }).setLngLat(js.Tuple2.fromScalaTuple2(markerDef.lngLat))
        .addTo(this)
      marker.getElement().addEventListener(
        "click",
        (e: dom.MouseEvent) => {
          e.stopPropagation()
          this.onMarkerClick.foreach(_(markerDef.id))
        }
      )

      this.markers.put(markerDef.id, marker)
    }

    private def clearMarkers(): Unit = {
      this.markers.values.foreach(_.remove())
      this.markers.clear()
    }

    private def removeMarker(id: String): Unit =
      this.markers.remove(id).foreach(_.remove())
  }

  case class MarkerDef(
      id: String,
      lngLat: (Double, Double),
      html: dom.Element,
      className: Option[String] = None,
      label: Option[String] = None
  )
}
