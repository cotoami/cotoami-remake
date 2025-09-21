package marubinotto.components

import scala.collection.immutable.TreeMap
import scala.collection.mutable.{Map => MutableMap}
import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.document.createElement

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import marubinotto.Action
import marubinotto.libs.geomap
import marubinotto.libs.geomap.maplibre
import marubinotto.libs.geomap.maplibre._
import marubinotto.libs.geomap.pmtiles

@react object MapLibre {

  val PathDefaultPmtiles = "/geomap/planet.pmtiles"
  val PathDefaultGlyphsDir = "/geomap/fonts"
  val PathDefaultSpritesDir = "/geomap/sprites"

  // Enable to load PMTiles files.
  //
  // addProtocol works best if it is only called once in the lifecycle of your application.
  // https://docs.protomaps.com/pmtiles/maplibre
  val protocol = new pmtiles.Protocol()
  maplibre.addProtocol("pmtiles", protocol.tile)

  case class Props(
      id: String,
      resolveResource: String => Option[String],
      disableRotation: Boolean = true,
      flavor: String = "light",
      lang: String,

      // Center/Zoom
      center: LngLat,
      zoom: Double,
      detectZoomClass: Option[Double => Option[String]] = None,

      // Markers
      focusedLocation: Option[LngLat] = None,
      markerDefs: TreeMap[String, MarkerDef] = TreeMap.empty,
      focusedMarkerId: Option[String] = None,

      // GeoBounds
      bounds: Option[LngLatBounds] = None,
      paddingToBounds: Double = 30,

      // Triggers to invoke effects
      // Changing the following values will trigger an effect.
      createMap: Action[Unit] = Action.default,
      applyCenterZoom: Action[Unit] = Action.default,
      fitBounds: Action[Unit] = Action.default,
      refreshMarkers: Action[Unit] = Action.default,
      updateMarker: Action[String] = Action.default,

      // Map resources
      styleLocation: String = "/geomap/style.json",
      pmtilesUrl: Option[String] = None,

      // Event handlers (which will be registered during map or marker initialization)
      onInit: Option[LngLatBounds => Unit] = None,
      onClick: Option[MapMouseEvent => Unit] = None,
      onZoomChanged: Option[Double => Unit] = None,
      onCenterMoved: Option[LngLat => Unit] = None,
      onBoundsChanged: Option[LngLatBounds => Unit] = None,
      onMarkerClick: Option[String => Unit] = None,
      onFocusedLocationClick: Option[() => Unit] = None
  ) {
    val pmtilesFullUrl: String = PmtilesUrlPrefix + pmtilesUrl.getOrElse(
      resolveResource(PathDefaultPmtiles).get
    )

    val glyphsUrl: String =
      s"${resolveResource(PathDefaultGlyphsDir).get}/{fontstack}/{range}.pbf"

    val spriteUrl: String =
      s"${resolveResource(PathDefaultSpritesDir).get}/v4/${flavor}"

    def markerToUpdate: Option[MarkerDef] =
      updateMarker.parameter.flatMap(markerDefs.get)
  }

  val component = FunctionalComponent[Props] { props =>
    val (mapInitialized, setMapInitialized) = useState[Boolean](false)
    val (zoomClass, setZoomClass) = useState[Option[String]](None)

    val mapRef = useRef[Option[ExtendedMap]](None)

    // To track the map state to detect change
    val boundsRef = useRef[Option[LngLatBounds]](None)

    // createMap
    useEffect(
      () => {
        // Since initMap() will be called via tauri.path.resourceDir() and
        // js.timers.setTimeout(), cleanup() can be called before or during initMap().
        // The effectCancelled flag allows us to detect this early cleanup(), and
        // stop executing initMap() or re-cleanup() after initMap() has finished.
        var effectCancelled = false

        val cleanup = () => {
          effectCancelled = true
          mapRef.current.foreach(_.remove())
          mapRef.current = None
          setMapInitialized(false)
        }

        val onClick: js.Function1[MapMouseEvent, Unit] =
          e => props.onClick.foreach(_(e))

        val detectBoundsChange = (e: MapLibreEvent) => {
          props.onBoundsChanged.foreach { onBoundsChanged =>
            val bounds = e.target.getBounds()
            if (Some(bounds).toString() != boundsRef.current.toString()) {
              boundsRef.current = Some(bounds)
              onBoundsChanged(bounds)
            }
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

        val initMap = () => {
          val mapStyle = geomap.basemapsStyle(
            props.pmtilesFullUrl,
            props.flavor,
            props.lang,
            props.glyphsUrl,
            props.spriteUrl
          )

          val map = new ExtendedMap(
            options = new MapOptions {
              override val container = props.id
              override val zoom = props.zoom
              override val center = props.center
              override val style = mapStyle
            },
            onMarkerClick = props.onMarkerClick,
            onFocusedLocationClick = props.onFocusedLocationClick
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
          map.addOrRemoveMarkers(props.markerDefs.values)

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

          // Map init completed
          mapRef.current = Some(map)
          boundsRef.current = Some(map.getBounds())
          props.onInit.map(_(map.getBounds()))
          setMapInitialized(true)

          if (effectCancelled) {
            println("The effect has been cancelled during initMap()")
            cleanup()
          }
        }

        // Delay rendering the map to ensure it to fit to the container section.
        js.timers.setTimeout(10) {
          if (effectCancelled) {
            println("The effect has been cancelled before initMap()")
          } else {
            initMap()
          }
        }

        cleanup
      },
      Seq(props.createMap.triggered)
    )

    // Effects that require a Map instance.
    //
    // Some of the props can be changed during Map initialization (ex. page's first
    // load or reload). if an effect depends on one of those props,
    // there can be cases where it doesn't take effect for an undefined Map, unless
    // you specify `mapInitialized` as a dependency so that it will be invoked again
    // after Map initialization has completed.

    // Update focused location marker
    useEffect(
      () => {
        mapRef.current.foreach(_.focusOrUnfocusLocation(props.focusedLocation))
      },
      Seq(
        props.focusedLocation.toString(),
        mapInitialized // `props.focusedLocation` can be changed during map init.
      )
    )

    // applyCenterZoom
    useEffect(
      () => {
        mapRef.current.foreach(
          _.flyTo(new FlyToOptions {
            override val center = props.center
            override val zoom = props.zoom
          })
        )
      },
      Seq(props.applyCenterZoom.triggered)
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
      Seq(props.fitBounds.triggered)
    )

    // addOrRemoveMarkers
    useEffect(
      () => {
        mapRef.current.foreach { map =>
          map.addOrRemoveMarkers(props.markerDefs.values)
          map.focusOrUnfocusMarker(props.focusedMarkerId)
        }
      },
      Seq(
        props.markerDefs.keySet.toString(),
        mapInitialized // `props.markerDefs` can be changed during map init.
      )
    )

    // refreshMarkers
    useEffect(
      () => {
        if (props.refreshMarkers.triggered > 0) { // prevent being executed during init
          mapRef.current.foreach { map =>
            map.refreshMarkers(props.markerDefs.values)
            map.focusOrUnfocusMarker(props.focusedMarkerId)
          }
        }
      },
      Seq(props.refreshMarkers.triggered)
    )

    // updateMarker
    useEffect(
      () => {
        props.markerToUpdate.foreach(markerDef =>
          mapRef.current.foreach { map =>
            map.putMarker(markerDef)
            map.focusOrUnfocusMarker(props.focusedMarkerId)
          }
        )
      },
      Seq(props.updateMarker.triggered)
    )

    // Focus/Unfocus marker
    useEffect(
      () => {
        mapRef.current.foreach(_.focusOrUnfocusMarker(props.focusedMarkerId))
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
  private val PmtilesUrlPrefix = "pmtiles://"
  private val VectorTilesUrlPlaceHolder = "$mainVectorTilesUrl"
  private val FocusedLocationMarkerClassName = "focused-location-marker"
  private val FocusedMarkerClassName = "focused-marker"

  private def isUrl(string: String): Boolean =
    UrlRegex.findFirstIn(string).isDefined

  class ExtendedMap(
      options: MapOptions,
      onMarkerClick: Option[String => Unit] = None,
      onFocusedLocationClick: Option[() => Unit] = None
  ) extends Map(options) {
    var focusedLocationMarker: Option[Marker] = None
    val markers: MutableMap[String, Marker] = MutableMap.empty
    var focusedMarkerId: Option[String] = None

    def disableRotation(): Unit = {
      dragRotate.disable()
      keyboard.disable()
      touchZoomRotate.disableRotation()
    }

    def focusLocation(lngLat: LngLat): Unit = {
      unfocusLocation()
      val marker = new Marker()
        .setLngLat(lngLat)
        .addTo(this)
      marker.addClassName(FocusedLocationMarkerClassName)
      marker.getElement().addEventListener(
        "click",
        (e: dom.MouseEvent) => {
          e.stopPropagation()
          onFocusedLocationClick.foreach(_())
        }
      )
      focusedLocationMarker = Some(marker)
    }

    def unfocusLocation(): Unit =
      focusedLocationMarker.foreach(_.remove())

    def focusOrUnfocusLocation(lngLat: Option[LngLat]): Unit =
      lngLat match {
        case Some(lngLat) => focusLocation(lngLat)
        case None         => unfocusLocation()
      }

    def putMarker(markerDef: MarkerDef): Unit = {
      removeMarker(markerDef.id)
      val marker = markerDef.createMarker(this, onMarkerClick)
      markers.put(markerDef.id, marker)
    }

    def clearMarkers(): Unit = {
      markers.values.foreach(_.remove())
      markers.clear()
    }

    def removeMarker(id: String): Unit =
      markers.remove(id).foreach(_.remove())

    def focusMarker(id: String): Unit = {
      unfocusMarker()
      markers.get(id).foreach { marker =>
        marker.addClassName(FocusedMarkerClassName)
        focusedMarkerId = Some(id)
      }
    }

    def unfocusMarker(): Unit = {
      focusedMarkerId.foreach(
        markers.get(_).foreach(_.removeClassName(FocusedMarkerClassName))
      )
      focusedMarkerId = None
    }

    def focusOrUnfocusMarker(id: Option[String]): Unit =
      id match {
        case Some(id) => focusMarker(id)
        case None     => unfocusMarker()
      }

    def addOrRemoveMarkers(markerDefs: Iterable[MarkerDef]): Unit = {
      val defMap = markerDefs.map(d => d.id -> d).toMap

      // Add
      val toAdd = defMap.keySet.diff(markers.keySet)
      toAdd.flatMap(defMap.get).foreach(putMarker)

      // Remove
      val toRemove = markers.keySet.diff(defMap.keySet)
      toRemove.foreach(removeMarker)
    }

    def refreshMarkers(markerDefs: Iterable[MarkerDef]): Unit = {
      clearMarkers()
      markerDefs.foreach(putMarker)
    }
  }

  case class MarkerDef(
      id: String,
      lngLat: (Double, Double),
      markerElement: dom.Element,
      popupHtml: Option[String]
  ) {
    def createMarker(
        map: Map,
        onClick: Option[String => Unit] = None
    ): Marker = {
      val jsLngLat = js.Tuple2.fromScalaTuple2(lngLat)

      // Create a fresh root element that holds the event listeners,
      // which will be registered below.
      val markerRootElement =
        createElement("div").asInstanceOf[dom.HTMLDivElement]
      markerRootElement.setAttribute("class", "marker-root")
      markerRootElement.append(markerElement)

      val marker = new Marker(new MarkerOptions() {
        override val element = markerRootElement
      }).setLngLat(jsLngLat).addTo(map)

      marker.getElement().addEventListener(
        "click",
        (e: dom.MouseEvent) => {
          e.stopPropagation()
          onClick.foreach(_(id))
        }
      )

      popupHtml.foreach { html =>
        val popup = new Popup(new PopupOptions() {
          override val closeButton = false
          override val closeOnClick = false
        })
        marker.getElement().addEventListener(
          "mouseenter",
          (e: dom.MouseEvent) => {
            popup.setLngLat(jsLngLat).setHTML(html).addTo(map)
          }
        )
        marker.getElement().addEventListener(
          "mouseleave",
          (e: dom.MouseEvent) => {
            popup.remove()
          }
        )
      }

      marker
    }
  }
}
