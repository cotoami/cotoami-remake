package cotoami.repositories

import scala.scalajs.js
import com.softwaremill.quicklens._
import fui.FunctionalUI._
import cotoami.DomainMsg
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
    this
      .modify(_.map).using(_ + (cotonoma.id -> cotonoma))
      .modify(_.mapByCotoId).using(_ + (cotonoma.cotoId -> cotonoma))
  }

  def addAll(jsons: js.Array[CotonomaJson]): Cotonomas =
    jsons.foldLeft(this)(_ add _)

  def importFrom(data: CotosRelatedDataJson): Cotonomas =
    this
      .addAll(data.posted_in)
      .addAll(data.as_cotonomas)

  def importFrom(graph: CotoGraphJson): Cotonomas =
    this
      .add(graph.root)
      .importFrom(graph.cotos_related_data)

  def setCotonomaDetails(details: CotonomaDetailsJson): Cotonomas = {
    val cotonoma = Cotonoma(details.cotonoma)
    this
      .deselect()
      .add(details.cotonoma)
      .addAll(details.supers)
      .addAll(details.subs.rows)
      .select(cotonoma.id)
      .modify(_.superIds).setTo(
        details.supers.map(json => Id[Cotonoma](json.uuid)).toSeq
      )
      .modify(_.subIds).using(
        _.addPage(
          details.subs,
          (json: CotonomaJson) => Id[Cotonoma](json.uuid)
        )
      )
  }

  def asCotonoma(coto: Coto): Option[Cotonoma] =
    if (coto.isCotonoma)
      this.getByCotoId(coto.repostOfId.getOrElse(coto.id))
    else
      None

  // You can select a cotonoma even if it's not contained in this repository.
  // In that case, it assumes that the cotonoma is being fetched at the same time.
  def select(id: Id[Cotonoma]): Cotonomas =
    this.deselect().copy(selectedId = Some(id))

  def deselect(): Cotonomas =
    this.copy(selectedId = None, superIds = Seq.empty, subIds = PaginatedIds())

  def isSelecting(id: Id[Cotonoma]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Cotonoma] = this.selectedId.flatMap(this.get)

  def supers: Seq[Cotonoma] = this.superIds.map(this.get).flatten

  def subs: Seq[Cotonoma] = this.subIds.order.map(this.get).flatten

  def addPageOfSubs(page: PaginatedJson[CotonomaJson]): Cotonomas =
    this
      .addAll(page.rows)
      .modify(_.subsLoading).setTo(false)
      .modify(_.subIds).using(
        _.addPage(
          page,
          (json: CotonomaJson) => Id[Cotonoma](json.uuid)
        )
      )

  def recent: Seq[Cotonoma] = this.recentIds.order.map(this.get).flatten

  def addPageOfRecent(page: PaginatedJson[CotonomaJson]): Cotonomas =
    this
      .addAll(page.rows)
      .modify(_.recentLoading).setTo(false)
      .modify(_.recentIds).using(
        _.addPage(
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
      result: Either[ErrorJson, PaginatedJson[CotonomaJson]]
  ) extends Msg
  case class FetchMoreSubs(id: Id[Cotonoma]) extends Msg
  case class SubsFetched(
      result: Either[ErrorJson, PaginatedJson[CotonomaJson]]
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
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex)).map(
      (RecentFetched andThen Domain.CotonomasMsg andThen DomainMsg)
    )

  def fetchDetails(id: Id[Cotonoma]): Cmd[cotoami.Msg] =
    Commands.send(Commands.Cotonoma(id)).map(
      Domain.CotonomaDetailsFetched andThen DomainMsg
    )

  def fetchSubs(id: Id[Cotonoma], pageIndex: Double): Cmd[cotoami.Msg] =
    Commands.send(Commands.SubCotonomas(id, pageIndex)).map(
      (SubsFetched andThen Domain.CotonomasMsg andThen DomainMsg)
    )
}
