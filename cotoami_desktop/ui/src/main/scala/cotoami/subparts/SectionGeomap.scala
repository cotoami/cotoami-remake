package cotoami.subparts

import scala.util.chaining._
import org.scalajs.dom
import org.scalajs.dom.document.createElement
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{CenterOrBounds, GeoBounds, Geolocation, Id}
import cotoami.repositories.Domain
import cotoami.backend.{ErrorJson, GeolocatedCotos}
import cotoami.components.{optionalClasses, MapLibre}

object SectionGeomap {

  case class Model(
      // Center/Zoom
      center: Option[Geolocation] = None,
      zoom: Option[Double] = None,
      _applyCenterZoom: Int = 0,

      // Bounds
      currentBounds: Option[GeoBounds] = None,
      bounds: Option[GeoBounds] = None,
      _fitBounds: Int = 0,

      // Focus
      focusedLocation: Option[Geolocation] = None,

      // Coto fetching
      initialCotosFetched: Boolean = false,
      nextBoundsToFetch: Option[GeoBounds] = None,
      fetchingCotosInBounds: Boolean = false,

      // Marker operations
      _addOrRemoveMarkers: Int = 0,
      _refreshMarkers: Int = 0
  ) {
    def applyCenterZoom: Model =
      copy(_applyCenterZoom = _applyCenterZoom + 1)

    def moveTo(location: Geolocation): Model =
      copy(
        center = Some(location),
        zoom = Some(13),
        _applyCenterZoom = _applyCenterZoom + 1
      )

    def moveTo(centerOrBounds: CenterOrBounds): Model =
      centerOrBounds match {
        case Left(location) => moveTo(location)
        case Right(bounds)  => fitBounds(bounds)
      }

    def fitBounds: Model =
      copy(_fitBounds = _fitBounds + 1)

    def fitBounds(bounds: GeoBounds): Model =
      copy(bounds = Some(bounds)).fitBounds

    def focus(location: Geolocation): Model =
      if (currentBounds.map(_.contains(location)).getOrElse(false))
        copy(focusedLocation = Some(location))
      else
        moveTo(location).copy(focusedLocation = Some(location))

    def unfocus: Model = copy(focusedLocation = None)

    def addOrRemoveMarkers: Model =
      copy(_addOrRemoveMarkers = _addOrRemoveMarkers + 1)

    def refreshMarkers: Model =
      copy(_refreshMarkers = _refreshMarkers + 1)

    def fetchCotosInBounds(bounds: GeoBounds): (Model, Cmd.One[AppMsg]) =
      if (initialCotosFetched && !fetchingCotosInBounds)
        (
          copy(fetchingCotosInBounds = true),
          GeolocatedCotos.inGeoBounds(bounds)
            .map(Msg.CotosInBoundsFetched(_).into)
        )
      else
        (
          // To avoid simultaneous fetchings, defer this fetch to the next round.
          // The current waiting bounds will be replaced with the new one.
          copy(nextBoundsToFetch = Some(bounds)),
          Cmd.none
        )

    def fetchCotosInCurrentBounds: (Model, Cmd.One[AppMsg]) =
      currentBounds match {
        case Some(currentBounds) => fetchCotosInBounds(currentBounds)
        case None                => (this, Cmd.none)
      }
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    case class Init(bounds: GeoBounds) extends Msg
    case class LocationClicked(location: Geolocation) extends Msg
    case class ZoomChanged(zoom: Double) extends Msg
    case class CenterMoved(center: Geolocation) extends Msg
    case class BoundsChanged(bounds: GeoBounds) extends Msg
    case class InitialCotosFetched(
        result: Either[ErrorJson, GeolocatedCotos]
    ) extends Msg
    case class CotosInBoundsFetched(
        result: Either[ErrorJson, GeolocatedCotos]
    ) extends Msg
    case class MarkerClicked(id: String) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Domain, Cmd[AppMsg]) = {
    val default = (model, context.domain, Cmd.none)
    msg match {
      case Msg.Init(bounds) =>
        (context.domain.geolocationInFocus match {
          case Some(location) => (model.moveTo(location), Cmd.none)
          case None           => model.fetchCotosInCurrentBounds
        }) pipe { case (model, cmd) =>
          default.copy(
            _1 = model
              .modify(_.currentBounds).setTo(Some(bounds))
              .addOrRemoveMarkers,
            _3 = cmd
          )
        }

      case Msg.LocationClicked(location) =>
        default.copy(_1 = model.copy(focusedLocation = Some(location)))

      case Msg.ZoomChanged(zoom) =>
        default.copy(_1 = model.copy(zoom = Some(zoom)))

      case Msg.CenterMoved(center) =>
        default.copy(_1 = model.copy(center = Some(center)))

      case Msg.BoundsChanged(bounds) => {
        val (geomap, fetch) = model.fetchCotosInBounds(bounds)
        default.copy(
          _1 = geomap.copy(currentBounds = Some(bounds)),
          _3 = fetch
        )
      }

      case Msg.InitialCotosFetched(Right(cotos)) => {
        val domain = context.domain.importFrom(cotos)
        (domain.geolocationInFocus match {
          case Some(location) => (model.moveTo(location), Cmd.none)
          case None           => model.fetchCotosInCurrentBounds
        }) pipe { case (model, cmd) =>
          default.copy(
            _1 = model
              .modify(_.initialCotosFetched).setTo(true)
              // Force to refresh when the cotonoma has been changed
              // (ex. marker's `in-focus` state could be changed)
              .refreshMarkers,
            _2 = context.domain.importFrom(cotos),
            _3 = cmd
          )
        }
      }

      case Msg.InitialCotosFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(initialCotosFetched = true),
          _3 = cotoami.error("Couldn't fetch geolocated cotos.", e)
        )

      case Msg.CotosInBoundsFetched(Right(cotos)) =>
        model.copy(fetchingCotosInBounds = false).pipe { model =>
          model.nextBoundsToFetch match {
            case Some(bounds) =>
              model.fetchCotosInBounds(bounds).pipe { case (model, fetchNext) =>
                (model.copy(nextBoundsToFetch = None), fetchNext)
              }
            case None => (model, Cmd.none)
          }
        }.pipe { case (model, fetchNext) =>
          default.copy(
            _1 = model.addOrRemoveMarkers,
            _2 = context.domain.importFrom(cotos),
            _3 = fetchNext
          )
        }

      case Msg.CotosInBoundsFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(fetchingCotosInBounds = false),
          _3 = cotoami.error("Couldn't fetch cotos in the bounds.", e)
        )

      case Msg.MarkerClicked(id) =>
        id.split(IdSeparator).toSeq match {
          case Seq(id) =>
            context.uiState match {
              case Some(uiState) if uiState.paneOpened(PaneFlow.PaneName) =>
                default.copy(_3 = Browser.send(AppMsg.FocusCoto(Id(id), false)))
              case _ =>
                default.copy(_3 =
                  Browser.send(
                    SectionTraversals.Msg.OpenTraversal(Id(id)).into
                  )
                )
            }
          case ids =>
            default.copy(_3 =
              Cmd.Batch.fromSeq(
                ids.map(id =>
                  Browser.send(
                    SectionTraversals.Msg.OpenTraversal(Id(id)).into
                  )
                )
              )
            )
        }
    }
  }

  def fetchInitialCotos(context: Context): Cmd.One[AppMsg] =
    GeolocatedCotos.fetch(
      context.domain.nodes.focusedId,
      context.domain.cotonomas.focusedId
    ).map(Msg.InitialCotosFetched(_).into)

  private def toMarkerDefs(
      markers: Seq[Geolocation.MarkerOfCotos]
  ): Seq[MapLibre.MarkerDef] =
    markers.map(toMarkerDef(_))

  val IdSeparator = ","

  private def toMarkerDef(
      markerOfCotos: Geolocation.MarkerOfCotos
  ): MapLibre.MarkerDef =
    MapLibre.MarkerDef(
      markerOfCotos.cotos.map(_.id.uuid).mkString(IdSeparator),
      markerOfCotos.location.toLngLat,
      markerElement(
        markerOfCotos.nodeIconUrls.take(4),
        markerOfCotos.inFocus,
        markerOfCotos.cotos.size,
        markerOfCotos.containsCotonomas,
        markerOfCotos.label
      ),
      markerOfCotos.cotos match {
        case Seq(coto) =>
          popupHtml(
            if (coto.isCotonoma) None else coto.abbreviate,
            coto.mediaUrl.map(_._1)
          )
        case _ => None
      }
    )

  private def popupHtml(
      text: Option[String],
      imageUrl: Option[String]
  ): Option[String] = {
    val textHtml = text.flatMap { text =>
      if (text.isBlank())
        None
      else
        Some(s"""<div class="text">${text}</div>""")
    }
    val imageHtml =
      imageUrl.map(url => s"""<div class="image"><img src="${url}" /></div>""")

    if (textHtml.isEmpty && imageHtml.isEmpty)
      None
    else
      Some(
        s"""
        |<div class="geomap-marker-popup">
        | ${imageHtml.getOrElse("")}
        | ${textHtml.getOrElse("")}
        |</div>
        """.stripMargin
      )
  }

  private def markerElement(
      iconUrls: Set[String],
      inFocus: Boolean,
      countOfCotos: Int,
      containsCotonomas: Boolean,
      label: Option[String]
  ): dom.Element = {
    // div.geomap-marker
    val marker = createElement("div").asInstanceOf[dom.HTMLDivElement]
    marker.className = optionalClasses(
      Seq(
        ("geomap-marker", true),
        ("coto-marker", !containsCotonomas),
        ("cotonoma-marker", containsCotonomas),
        ("in-focus", inFocus)
      )
    )

    // div.icons
    val icons = createElement("div").asInstanceOf[dom.HTMLDivElement]
    icons.className = optionalClasses(
      Seq(
        ("icons", true),
        (s"icon-count-${iconUrls.size}", true)
      )
    )
    iconUrls.foreach { url =>
      val icon = createElement("img").asInstanceOf[dom.HTMLImageElement]
      icon.className = "icon"
      icon.src = url
      icons.append(icon)
    }
    marker.append(icons)

    // div.count-of-cotos
    if (countOfCotos > 1) {
      val count = createElement("div").asInstanceOf[dom.HTMLDivElement]
      count.className = "count-of-cotos"
      count.textContent = countOfCotos.toString()
      marker.append(count)
    }

    // div.label
    label.foreach { name =>
      val label = createElement("div").asInstanceOf[dom.HTMLDivElement]
      label.className = "label"
      label.textContent = name // using textContent can prevent XSS attacks.
      marker.append(label)
    }

    marker
  }

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    MapLibre(
      id = "main-geomap",
      center = model.center.getOrElse(Geolocation.default).toMapLibre,
      zoom = model.zoom.getOrElse(8),
      detectZoomClass = Some(zoom =>
        if (zoom <= 7)
          Some("hide-labels")
        else
          None
      ),
      focusedLocation = model.focusedLocation.map(_.toMapLibre),
      markerDefs = toMarkerDefs(context.domain.locationMarkers),
      focusedMarkerId = context.domain.cotos.focusedId.map(_.uuid),
      bounds = model.bounds.map(_.toMapLibre),
      applyCenterZoom = model._applyCenterZoom,
      addOrRemoveMarkers = model._addOrRemoveMarkers,
      refreshMarkers = model._refreshMarkers,
      fitBounds = model._fitBounds,
      onInit = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.Init(bounds))
      }),
      onClick = Some(e => {
        val location = Geolocation.fromMapLibre(e.lngLat)
        dispatch(Msg.LocationClicked(location))
      }),
      onZoomChanged = Some(zoom => dispatch(Msg.ZoomChanged(zoom))),
      onCenterMoved = Some(center => {
        val location = Geolocation.fromMapLibre(center)
        dispatch(Msg.CenterMoved(location))
      }),
      onBoundsChanged = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.BoundsChanged(bounds))
      }),
      onMarkerClick = Some(id => dispatch(Msg.MarkerClicked(id)))
    )
  }
}
