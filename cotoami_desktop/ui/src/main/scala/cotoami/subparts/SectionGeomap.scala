package cotoami.subparts

import scala.util.chaining._
import org.scalajs.dom
import org.scalajs.dom.document.createElement
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement

import fui.{Browser, Cmd}
import cotoami.{log_info, Context, Msg => AppMsg}
import cotoami.models.{GeoBounds, Geolocation, Id}
import cotoami.repositories.Domain
import cotoami.backend.{ErrorJson, GeolocatedCotos}
import cotoami.components.{optionalClasses, MapLibre}

object SectionGeomap {

  case class Model(
      // Center/Zoom
      center: Option[Geolocation] = None,
      zoom: Option[Double] = None,
      _applyCenterZoom: Int = 0,

      // Focus
      focusedLocation: Option[Geolocation] = None,

      // Bounds
      currentBounds: Option[GeoBounds] = None,
      bounds: Option[GeoBounds] = None,
      _fitBounds: Int = 0,

      // Cotonoma location
      cotonomaLocation: Option[CotonomaLocation] = None,

      // Coto fetching
      initialCotosFetched: Boolean = false,
      nextBoundsToFetch: Option[GeoBounds] = None,
      fetchingCotosInBounds: Boolean = false,

      // Marker operations
      _addOrRemoveMarkers: Int = 0,
      _refreshMarkers: Int = 0
  ) {
    def applyCenterZoom: Model =
      this.copy(_applyCenterZoom = this._applyCenterZoom + 1)

    def moveTo(location: Geolocation): Model =
      this.copy(
        center = Some(location),
        zoom = Some(13),
        _applyCenterZoom = this._applyCenterZoom + 1
      )

    def focus(location: Geolocation): Model =
      if (this.currentBounds.map(_.contains(location)).getOrElse(false))
        this.copy(focusedLocation = Some(location))
      else
        this.moveTo(location).copy(focusedLocation = Some(location))

    def unfocus: Model = this.copy(focusedLocation = None)

    def moveToCotonomaLocation: Model =
      this.cotonomaLocation match {
        case Some(location) =>
          location match {
            case CotonomaCenter(location) => moveTo(location)
            case CotonomaBounds(bounds)   => fitBounds(bounds)
          }
        case None => this
      }

    def addOrRemoveMarkers: Model =
      this.copy(_addOrRemoveMarkers = this._addOrRemoveMarkers + 1)

    def refreshMarkers: Model =
      this.copy(_refreshMarkers = this._refreshMarkers + 1)

    def fitBounds: Model =
      this.copy(_fitBounds = this._fitBounds + 1)

    def fitBounds(bounds: GeoBounds): Model =
      this.copy(bounds = Some(bounds)).fitBounds

    def fetchCotosInBounds(bounds: GeoBounds): (Model, Cmd[AppMsg]) =
      if (this.initialCotosFetched && !this.fetchingCotosInBounds)
        (
          this.copy(fetchingCotosInBounds = true),
          GeolocatedCotos.inGeoBounds(bounds)
            .map(Msg.toApp(Msg.CotosInBoundsFetched(_)))
        )
      else
        (
          // To avoid simultaneous fetchings, defer this fetch to the next round.
          // The current waiting bounds will be replaced with the new one.
          this.copy(nextBoundsToFetch = Some(bounds)),
          Cmd.none
        )

    def fetchCotosInCurrentBounds: (Model, Cmd[AppMsg]) =
      this.currentBounds match {
        case Some(currentBounds) => fetchCotosInBounds(currentBounds)
        case None                => (this, Cmd.none)
      }
  }

  sealed trait CotonomaLocation
  case class CotonomaCenter(location: Geolocation) extends CotonomaLocation
  case class CotonomaBounds(bounds: GeoBounds) extends CotonomaLocation

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.SectionGeomapMsg(this)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): (T => AppMsg) =
      tagger andThen AppMsg.SectionGeomapMsg

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
  ): (Model, Domain, Seq[Cmd[AppMsg]]) = {
    val default = (model, context.domain, Seq.empty)
    msg match {
      case Msg.Init(bounds) =>
        default.copy(_1 =
          model
            .modify(_.currentBounds).setTo(Some(bounds))
            .addOrRemoveMarkers
        ).pipe(moveToCotonomaOrFetchInCurrentBounds)

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
          _3 = Seq(fetch)
        )
      }

      case Msg.InitialCotosFetched(Right(cotos)) => {
        val center = context.domain.currentCotonomaCoto.flatMap(_.geolocation)
        val cotonomaLocation = center match {
          case Some(center) => Some(CotonomaCenter(center))
          case None =>
            cotos.geoBounds match {
              case Some(Right(bounds))  => Some(CotonomaBounds(bounds))
              case Some(Left(location)) => Some(CotonomaCenter(location))
              case None                 => None
            }
        }
        default.copy(
          _1 = model
            .modify(_.initialCotosFetched).setTo(true)
            .modify(_.cotonomaLocation).setTo(cotonomaLocation)
            .refreshMarkers,
          _2 = context.domain.importFrom(cotos),
          _3 = Seq(log_info(s"Geolocated cotos fetched.", Some(cotos.debug)))
        ).pipe(moveToCotonomaOrFetchInCurrentBounds)
      }

      case Msg.InitialCotosFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(initialCotosFetched = true),
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch geolocated cotos."))
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
            _3 = Seq(
              fetchNext,
              log_info(s"Cotos in the bounds fetched.", Some(cotos.debug))
            )
          )
        }

      case Msg.CotosInBoundsFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(fetchingCotosInBounds = false),
          _3 = Seq(ErrorJson.log(e, "Couldn't fetch cotos in the bounds."))
        )

      case Msg.MarkerClicked(id) =>
        id.split(IdSeparator).toSeq match {
          case Seq(id) =>
            context.uiState match {
              case Some(uiState) if uiState.paneOpened(PaneFlow.PaneName) =>
                default.copy(_3 = Seq(Browser.send(AppMsg.FocusCoto(Id(id)))))
              case _ =>
                default.copy(_3 =
                  Seq(
                    Browser.send(
                      SectionTraversals.Msg.OpenTraversal(Id(id)).toApp
                    )
                  )
                )
            }
          case ids =>
            default.copy(_3 =
              ids.map(id =>
                Browser.send(
                  SectionTraversals.Msg.OpenTraversal(Id(id)).toApp
                )
              )
            )
        }
    }
  }

  private def moveToCotonomaOrFetchInCurrentBounds(
      result: (Model, Domain, Seq[Cmd[AppMsg]])
  ): (Model, Domain, Seq[Cmd[AppMsg]]) =
    result.pipe { case (model, domain, cmds) =>
      if (model.cotonomaLocation.isDefined)
        (model.moveToCotonomaLocation, domain, cmds)
      else
        model.fetchCotosInCurrentBounds.pipe { case (model, cmd) =>
          (model, domain, cmds :+ cmd)
        }
    }

  def fetchInitialCotos()(implicit context: Context): Cmd[AppMsg] =
    GeolocatedCotos.fetch(
      context.domain.nodes.focusedId,
      context.domain.cotonomas.focusedId
    ).map(
      Msg.toApp(Msg.InitialCotosFetched(_))
    )

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
      None
    )

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
      label.textContent = name
      marker.append(label)
    }

    marker
  }

  def apply(
      model: Model
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
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
        dispatch(Msg.Init(bounds).toApp)
      }),
      onClick = Some(e => {
        val location = Geolocation.fromMapLibre(e.lngLat)
        dispatch(Msg.LocationClicked(location).toApp)
      }),
      onZoomChanged = Some(zoom => dispatch(Msg.ZoomChanged(zoom).toApp)),
      onCenterMoved = Some(center => {
        val location = Geolocation.fromMapLibre(center)
        dispatch(Msg.CenterMoved(location).toApp)
      }),
      onBoundsChanged = Some(lngLatBounds => {
        val bounds = GeoBounds.fromMapLibre(lngLatBounds)
        dispatch(Msg.BoundsChanged(bounds).toApp)
      }),
      onMarkerClick = Some(id => dispatch(Msg.MarkerClicked(id).toApp))
    )
  }
}
