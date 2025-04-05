package cotoami.subparts

import scala.util.chaining._
import scala.collection.immutable.TreeMap
import org.scalajs.dom
import org.scalajs.dom.document.createElement
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement

import marubinotto.fui.{Browser, Cmd}
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{CenterOrBounds, CotoMarker, GeoBounds, Geolocation, Id}
import cotoami.repository.Root
import cotoami.backend.{ErrorJson, GeolocatedCotos}
import cotoami.components.{optionalClasses, Action, MapLibre}

object SectionGeomap {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      // Center/Zoom
      center: Option[Geolocation] = None,
      zoom: Option[Double] = None,

      // Bounds
      currentBounds: Option[GeoBounds] = None,
      bounds: Option[GeoBounds] = None,

      // Focus
      focusedLocation: Option[Geolocation] = None,

      // Coto fetching
      fetchingCotosInFocus: Boolean = false,
      fetchingCotosInBounds: Boolean = false,

      // Actions
      _createMap: Action[Unit] = Action.default,
      _applyCenterZoom: Action[Unit] = Action.default,
      _fitBounds: Action[Unit] = Action.default,
      _refreshMarkers: Action[Unit] = Action.default,
      _updateMarker: Action[String] = Action.default
  ) {
    // If the new focus is on a cotonoma, the target cotonoma must
    // have been loaded before calling this method.
    def onFocusChange(repo: Root): (Model, Cmd.One[AppMsg]) =
      (
        unfocus.copy(fetchingCotosInFocus = true),
        GeolocatedCotos.fetch(
          repo.nodes.focusedId,
          repo.cotonomas.focusedId
        ).map(Msg.CotosInFocusFetched(_).into)
      )

    def recreateMap: Model =
      this.modify(_._createMap).using(_.trigger)

    def applyCenterZoom: Model =
      this.modify(_._applyCenterZoom).using(_.trigger)

    def moveTo(location: Geolocation): Model =
      copy(
        center = Some(location),
        zoom = Some(13)
      ).applyCenterZoom

    def moveTo(centerOrBounds: CenterOrBounds): Model =
      centerOrBounds match {
        case Left(location) => moveTo(location)
        case Right(bounds)  => fitBounds(bounds)
      }

    def fitBounds: Model =
      this.modify(_._fitBounds).using(_.trigger)

    def fitBounds(bounds: GeoBounds): Model =
      copy(bounds = Some(bounds)).fitBounds

    def focus(location: Geolocation): Model =
      if (currentBounds.map(_.contains(location)).getOrElse(false))
        copy(focusedLocation = Some(location))
      else
        moveTo(location).copy(focusedLocation = Some(location))

    def unfocus: Model = copy(focusedLocation = None)

    def focused: Option[Geolocation] = focusedLocation

    def isFocusing(location: Geolocation): Boolean =
      Some(location) == focusedLocation

    def refreshMarkers: Model =
      this.modify(_._refreshMarkers).using(_.trigger)

    def updateMarker(id: String): Model =
      this.modify(_._updateMarker).using(_.trigger(id))

    def fetchCotosInBounds(bounds: GeoBounds): (Model, Cmd.One[AppMsg]) =
      (
        copy(fetchingCotosInBounds = true),
        GeolocatedCotos.inGeoBounds(bounds)
          .map(Msg.CotosInBoundsFetched(_).into)
      )

    def fetchCotosInCurrentBounds: (Model, Cmd.One[AppMsg]) =
      currentBounds.map(fetchCotosInBounds).getOrElse((this, Cmd.none))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    case object RecreateMap extends Msg
    case class MapInitialized(bounds: GeoBounds) extends Msg
    case class FocusLocation(location: Option[Geolocation]) extends Msg
    case class ZoomChanged(zoom: Double) extends Msg
    case class CenterMoved(center: Geolocation) extends Msg
    case class BoundsChanged(bounds: GeoBounds) extends Msg
    case class CotosInFocusFetched(
        result: Either[ErrorJson, GeolocatedCotos]
    ) extends Msg
    case class CotosInBoundsFetched(
        result: Either[ErrorJson, GeolocatedCotos]
    ) extends Msg
    case class MarkerClicked(id: String) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Root, Cmd[AppMsg]) = {
    val default = (model, context.repo, Cmd.none)
    msg match {
      case Msg.RecreateMap => default.copy(_1 = model.recreateMap)

      // When a geomap is opened:
      case Msg.MapInitialized(bounds) =>
        model.copy(currentBounds = Some(bounds)).pipe { model =>
          // Move to the location calculated from the current focus:
          // Msg.CotosInFocusFetched should happen on each focus change,
          // so here, it just needs to get the location of the current focus.
          context.repo.geolocationInFocus
            .map(location => (model.moveTo(location), Cmd.none))
            .getOrElse(model.fetchCotosInCurrentBounds)
            .pipe { case (model, cmd) =>
              default.copy(_1 = model, _3 = cmd)
            }
        }

      case Msg.FocusLocation(location) =>
        default.copy(_1 = model.copy(focusedLocation = location))

      case Msg.ZoomChanged(zoom) =>
        default.copy(_1 = model.copy(zoom = Some(zoom)))

      case Msg.CenterMoved(center) =>
        default.copy(_1 = model.copy(center = Some(center)))

      case Msg.BoundsChanged(bounds) => {
        val (geomap, fetch) = model.fetchCotosInBounds(bounds)
        default.copy(
          _1 = geomap.copy(currentBounds = Some(bounds)),
          _3 = fetch.debounce("SectionGeomap.fetch", 200)
        )
      }

      case Msg.CotosInFocusFetched(Right(cotos)) => {
        context.repo.importFrom(cotos).pipe { repo =>
          repo.geolocationInFocus
            .map(location => (model.moveTo(location), Cmd.none))
            .getOrElse(model.fetchCotosInCurrentBounds)
            .pipe { case (model, cmd) =>
              default.copy(
                _1 = model.copy(fetchingCotosInFocus = false)
                  // Force to refresh when the focus has been changed
                  // (ex. marker's `in-focus` state could be changed)
                  .refreshMarkers,
                _2 = repo,
                _3 = cmd
              )
            }
        }
      }

      case Msg.CotosInFocusFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(fetchingCotosInFocus = false),
          _3 = cotoami.error("Couldn't fetch geolocated cotos in the focus.", e)
        )

      case Msg.CotosInBoundsFetched(Right(cotos)) =>
        default.copy(
          _1 = model.copy(fetchingCotosInBounds = false),
          _2 = context.repo.importFrom(cotos)
        )

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

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    MapLibre(
      id = "main-geomap",
      center = model.center.getOrElse(Geolocation.default).toMapLibre,
      zoom = model.zoom.getOrElse(4),
      detectZoomClass = Some(zoom =>
        if (zoom <= 7)
          Some("hide-labels")
        else
          None
      ),
      focusedLocation = model.focusedLocation.map(_.toMapLibre),
      markerDefs = toMarkerDefs(context.repo.cotoMarkers),
      focusedMarkerId = context.repo.cotos.focusedId.map(_.uuid),
      bounds = model.bounds.map(_.toMapLibre),
      createMap = model._createMap,
      applyCenterZoom = model._applyCenterZoom,
      refreshMarkers = model._refreshMarkers,
      updateMarker = model._updateMarker,
      fitBounds = model._fitBounds,
      onInit = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.MapInitialized(bounds))
      }),
      onClick = Some(e => {
        val location = Geolocation.fromMapLibre(e.lngLat)
        dispatch(Msg.FocusLocation(Some(location)))
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
      onMarkerClick = Some(id => dispatch(Msg.MarkerClicked(id))),
      onFocusedLocationClick = Some(() => dispatch(Msg.FocusLocation(None)))
    )
  }

  private def toMarkerDefs(
      markers: Seq[CotoMarker]
  ): TreeMap[String, MapLibre.MarkerDef] =
    TreeMap.from(markers.map(toMarkerDef).map(d => d.id -> d))

  val IdSeparator = ","

  private def toMarkerDef(cotoMarker: CotoMarker): MapLibre.MarkerDef =
    MapLibre.MarkerDef(
      cotoMarker.cotos.map(_.id.uuid).mkString(IdSeparator),
      cotoMarker.location.toLngLat,
      markerElement(
        cotoMarker.nodeIconUrls.take(4),
        cotoMarker.inFocus,
        cotoMarker.cotos.size,
        cotoMarker.containsCotonomas,
        cotoMarker.label
      ),
      cotoMarker.cotos match {
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
}
