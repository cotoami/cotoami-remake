package cotoami.repositories

import scala.scalajs.js
import fui.FunctionalUI._
import cotoami.{node_command, CotonomaDetailsFetched, CotonomasMsg}
import cotoami.backend._

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,
    mapByCotoId: Map[Id[Coto], Cotonoma] = Map.empty,

    // The currently selected cotonoma and its super/sub cotonomas
    selectedId: Option[Id[Cotonoma]] = None,
    superIds: Seq[Id[Cotonoma]] = Seq.empty,
    subIds: PaginatedIds[Cotonoma] = PaginatedIds(),
    subsLoading: Boolean = false,

    // Recent
    recentIds: PaginatedIds[Cotonoma] = PaginatedIds(),
    recentLoading: Boolean = false
) {
  def get(id: Id[Cotonoma]): Option[Cotonoma] = this.map.get(id)

  def getByCotoId(id: Id[Coto]): Option[Cotonoma] = this.mapByCotoId.get(id)

  def isEmpty: Boolean = this.map.isEmpty

  def contains(id: Id[Cotonoma]): Boolean = this.map.contains(id)

  def add(json: CotonomaJson): Cotonomas = {
    val cotonoma = Cotonoma(json)
    this.copy(
      map = this.map + (cotonoma.id -> cotonoma),
      mapByCotoId = this.mapByCotoId + (cotonoma.cotoId -> cotonoma)
    )
  }

  def addAll(jsons: js.Array[CotonomaJson]): Cotonomas =
    jsons.foldLeft(this)(_ add _)

  def importFrom(data: CotosRelatedDataJson): Cotonomas =
    this.addAll(data.posted_in ++ data.as_cotonomas)

  def setCotonomaDetails(details: CotonomaDetailsJson): Cotonomas = {
    val cotonoma = Cotonoma(details.cotonoma)
    val map = Cotonomas.toMap(details.supers) ++
      Cotonomas.toMap(details.subs.rows) +
      (cotonoma.id -> cotonoma)
    this.deselect().copy(
      selectedId = Some(cotonoma.id),
      superIds = details.supers.map(json => Id[Cotonoma](json.uuid)).toSeq,
      subIds = this.subIds.addPage(
        details.subs,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      ),
      map = this.map ++ map
    )
  }

  def asCotonoma(coto: Coto): Option[Cotonoma] =
    if (coto.isCotonoma)
      this.getByCotoId(coto.repostOfId.getOrElse(coto.id))
    else
      None

  def select(id: Id[Cotonoma]): Cotonomas =
    if (this.contains(id))
      this.deselect().copy(selectedId = Some(id))
    else
      this

  def deselect(): Cotonomas =
    this.copy(selectedId = None, superIds = Seq.empty, subIds = PaginatedIds())

  def isSelecting(id: Id[Cotonoma]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Cotonoma] = this.selectedId.flatMap(this.get)

  def supers: Seq[Cotonoma] = this.superIds.map(this.get).flatten

  def subs: Seq[Cotonoma] = this.subIds.order.map(this.get).flatten

  def addPageOfSubs(page: Paginated[CotonomaJson]): Cotonomas =
    this.addAll(page.rows).copy(
      subsLoading = false,
      subIds = this.subIds.addPage(
        page,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      )
    )

  def recent: Seq[Cotonoma] = this.recentIds.order.map(this.get).flatten

  def addPageOfRecent(page: Paginated[CotonomaJson]): Cotonomas =
    this.addAll(page.rows).copy(
      recentLoading = false,
      recentIds = this.recentIds.addPage(
        page,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      )
    )
}

object Cotonomas {
  def toMap(jsons: js.Array[CotonomaJson]): Map[Id[Cotonoma], Cotonoma] =
    jsons.map(json => (Id[Cotonoma](json.uuid), Cotonoma(json))).toMap

  sealed trait Msg
  case object FetchMoreRecent extends Msg
  case class RecentFetched(
      result: Either[ErrorJson, Paginated[CotonomaJson]]
  ) extends Msg
  case class FetchMoreSubs(id: Id[Cotonoma]) extends Msg
  case class SubsFetched(
      result: Either[ErrorJson, Paginated[CotonomaJson]]
  ) extends Msg

  def update(
      msg: Msg,
      model: Cotonomas,
      nodeId: Option[Id[Node]]
  ): (Cotonomas, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case FetchMoreRecent =>
        if (model.recentLoading) {
          (model, Seq.empty)
        } else {
          model.recentIds.nextPageIndex.map(i =>
            (
              model.copy(recentLoading = true),
              Seq(fetchRecent(nodeId, i))
            )
          ).getOrElse((model, Seq.empty))
        }

      case RecentFetched(Right(page)) =>
        (model.addPageOfRecent(page), Seq.empty)

      case RecentFetched(Left(e)) =>
        (
          model.copy(recentLoading = false),
          Seq(ErrorJson.log(e, "Couldn't fetch recent cotonomas."))
        )

      case FetchMoreSubs(id) =>
        if (model.subsLoading) {
          (model, Seq.empty)
        } else {
          model.subIds.nextPageIndex.map(i =>
            (
              model.copy(subsLoading = true),
              Seq(fetchSubs(id, i))
            )
          ).getOrElse((model, Seq.empty))
        }

      case SubsFetched(Right(page)) =>
        (model.addPageOfSubs(page), Seq.empty)

      case SubsFetched(Left(e)) =>
        (
          model.copy(subsLoading = false),
          Seq(ErrorJson.log(e, "Couldn't fetch sub cotonomas."))
        )
    }

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[cotoami.Msg] =
    node_command(Commands.RecentCotonomas(nodeId, pageIndex)).map(
      (RecentFetched andThen CotonomasMsg)
    )

  def fetchDetails(id: Id[Cotonoma]): Cmd[cotoami.Msg] =
    node_command(Commands.Cotonoma(id)).map(CotonomaDetailsFetched)

  def fetchSubs(id: Id[Cotonoma], pageIndex: Double): Cmd[cotoami.Msg] =
    node_command(Commands.SubCotonomas(id, pageIndex)).map(
      (SubsFetched andThen CotonomasMsg)
    )
}
