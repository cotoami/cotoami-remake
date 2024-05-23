package cotoami.repositories

import scala.scalajs.js
import com.softwaremill.quicklens._
import fui.FunctionalUI._
import cotoami.{log_info, DomainMsg}
import cotoami.backend._

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,
    mapByCotoId: Map[Id[Coto], Id[Cotonoma]] = Map.empty,

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

  def getByCotoId(id: Id[Coto]): Option[Cotonoma] =
    this.mapByCotoId.get(id).flatMap(this.get)

  def isEmpty: Boolean = this.map.isEmpty

  def contains(id: Id[Cotonoma]): Boolean = this.map.contains(id)

  def add(cotonoma: Cotonoma): Cotonomas = {
    this
      .modify(_.map).using(_ + (cotonoma.id -> cotonoma))
      .modify(_.mapByCotoId).using(_ + (cotonoma.cotoId -> cotonoma.id))
  }

  def addAll(cotonomas: Iterable[Cotonoma]): Cotonomas =
    cotonomas.foldLeft(this)(_ add _)

  def importFrom(data: CotosRelatedData): Cotonomas =
    this
      .addAll(data.postedIn)
      .addAll(data.asCotonomas)

  def importFrom(graph: CotoGraph): Cotonomas =
    graph.rootCotonoma.map(this.add).getOrElse(this)
      .importFrom(graph.cotosRelatedData)

  def setCotonomaDetails(details: CotonomaDetails): Cotonomas = {
    this
      .deselect()
      .add(details.cotonoma)
      .addAll(details.supers)
      .addAll(details.subs.rows)
      .select(details.cotonoma.id)
      .modify(_.superIds).setTo(details.supers.map(_.id).toSeq)
      .modify(_.subIds).using(_.appendPage(details.subs))
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

  def appendPageOfSubs(page: Paginated[Cotonoma, _]): Cotonomas =
    this
      .addAll(page.rows)
      .modify(_.subsLoading).setTo(false)
      .modify(_.subIds).using(_.appendPage(page))

  def recent: Seq[Cotonoma] = this.recentIds.order.map(this.get).flatten

  def appendPageOfRecent(page: Paginated[Cotonoma, _]): Cotonomas =
    this
      .addAll(page.rows)
      .modify(_.recentLoading).setTo(false)
      .modify(_.recentIds).using(_.appendPage(page))

  def prependToRecent(cotonoma: Cotonoma): Cotonomas =
    this
      .add(cotonoma)
      .modify(_.recentIds).using(_.prependId(cotonoma.id))

  def prependIdToRecent(id: Id[Cotonoma]): (Cotonomas, Seq[Cmd[cotoami.Msg]]) =
    (
      this.modify(_.recentIds).using(_.prependId(id)),
      if (!this.contains(id))
        Seq(Cotonomas.fetchOne(id))
      else
        Seq.empty
    )
}

object Cotonomas {
  def toMap(jsons: js.Array[CotonomaJson]): Map[Id[Cotonoma], Cotonoma] =
    jsons.map(json => (Id[Cotonoma](json.uuid), Cotonoma(json))).toMap

  sealed trait Msg
  case class OneFetched(result: Either[ErrorJson, CotonomaJson]) extends Msg
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
      case OneFetched(Right(cotonomaJson)) =>
        (
          model.add(Cotonoma(cotonomaJson)),
          Seq(log_info("Cotonoma fetched.", Some(cotonomaJson.name)))
        )

      case OneFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch a cotonoma.")))

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
        (model.appendPageOfRecent(Paginated(page, Cotonoma(_))), Seq.empty)

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
        (model.appendPageOfSubs(Paginated(page, Cotonoma(_))), Seq.empty)

      case SubsFetched(Left(e)) =>
        (
          model.copy(subsLoading = false),
          Seq(ErrorJson.log(e, "Couldn't fetch sub cotonomas."))
        )
    }

  def fetchOne(id: Id[Cotonoma]): Cmd[cotoami.Msg] =
    Commands.send(Commands.Cotonoma(id)).map(
      OneFetched andThen Domain.CotonomasMsg andThen DomainMsg
    )

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[cotoami.Msg] =
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex)).map(
      RecentFetched andThen Domain.CotonomasMsg andThen DomainMsg
    )

  def fetchSubs(id: Id[Cotonoma], pageIndex: Double): Cmd[cotoami.Msg] =
    Commands.send(Commands.SubCotonomas(id, pageIndex)).map(
      SubsFetched andThen Domain.CotonomasMsg andThen DomainMsg
    )
}
