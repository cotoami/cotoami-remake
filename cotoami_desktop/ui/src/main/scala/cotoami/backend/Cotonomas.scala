package cotoami.backend

import fui.FunctionalUI._
import cotoami.{node_command, CotonomasMsg, Id}

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,

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

  def contains(id: Id[Cotonoma]): Boolean = this.map.contains(id)

  def select(id: Id[Cotonoma]): Cotonomas =
    if (this.contains(id))
      this.deselect().copy(selectedId = Some(id))
    else
      this

  def setCotonomaDetails(details: CotonomaDetailsJson): Cotonomas = {
    val cotonoma = Cotonoma(details.cotonoma)
    val map = Cotonoma.toMap(details.supers) ++
      Cotonoma.toMap(details.subs.rows) +
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

  def deselect(): Cotonomas =
    this.copy(selectedId = None, superIds = Seq.empty, subIds = PaginatedIds())

  def isSelecting(id: Id[Cotonoma]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Cotonoma] = this.selectedId.flatMap(this.get(_))

  def supers: Seq[Cotonoma] = this.superIds.map(this.get(_)).flatten

  def subs: Seq[Cotonoma] = this.subIds.order.map(this.get(_)).flatten

  def addPageOfSubs(page: Paginated[CotonomaJson]): Cotonomas =
    this.copy(
      map = this.map ++ Cotonoma.toMap(page.rows),
      subIds = this.subIds.addPage(
        page,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      )
    )

  def recent: Seq[Cotonoma] = this.recentIds.order.map(this.get(_)).flatten

  def addPageOfRecent(page: Paginated[CotonomaJson]): Cotonomas =
    this.copy(
      map = this.map ++ Cotonoma.toMap(page.rows),
      recentIds = this.recentIds.addPage(
        page,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      )
    )
}

object Cotonomas {
  sealed trait Msg

  case object FetchMoreRecent extends Msg
  case class RecentFetched(
      result: Either[Error, Paginated[CotonomaJson]]
  ) extends Msg
  case class DetailsFetched(
      result: Either[Error, CotonomaDetailsJson]
  ) extends Msg
  case class FetchMoreSubs(id: Id[Cotonoma]) extends Msg
  case class SubsFetched(
      result: Either[Error, Paginated[CotonomaJson]]
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
        (
          model.addPageOfRecent(page).copy(recentLoading = false),
          Seq.empty
        )

      case RecentFetched(Left(e)) =>
        (
          model.copy(recentLoading = false),
          Seq(Error.log(e, "Couldn't fetch recent cotonomas."))
        )

      case DetailsFetched(Right(details)) =>
        (
          model.setCotonomaDetails(details),
          Seq.empty
        )

      case DetailsFetched(Left(e)) =>
        (
          model,
          Seq(Error.log(e, "Couldn't fetch cotonoma details."))
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
        (
          model.addPageOfSubs(page).copy(subsLoading = false),
          Seq.empty
        )

      case SubsFetched(Left(e)) =>
        (
          model.copy(subsLoading = false),
          Seq(Error.log(e, "Couldn't fetch sub cotonomas."))
        )
    }

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[cotoami.Msg] =
    node_command(Commands.RecentCotonomas(nodeId, pageIndex)).map(
      (RecentFetched andThen CotonomasMsg)(_)
    )

  def fetchDetails(id: Id[Cotonoma]): Cmd[cotoami.Msg] =
    node_command(Commands.Cotonoma(id)).map(
      (DetailsFetched andThen CotonomasMsg)(_)
    )

  def fetchSubs(id: Id[Cotonoma], pageIndex: Double): Cmd[cotoami.Msg] =
    node_command(Commands.SubCotonomas(id, pageIndex)).map(
      (SubsFetched andThen CotonomasMsg)(_)
    )
}
