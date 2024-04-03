package cotoami.backend

import cotoami.Id

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,

    // Recent
    recentIds: PaginatedIds[Coto] = PaginatedIds(),
    recentLoading: Boolean = false
) {
  def get(id: Id[Coto]): Option[Coto] = this.map.get(id)

  def recent: Seq[Coto] = this.recentIds.order.map(this.get).flatten

  def addPageOfRecent(page: Paginated[CotoJson]): Cotos =
    this.copy(
      map = this.map ++ Coto.toMap(page.rows),
      recentIds = this.recentIds.addPage(
        page,
        (json: CotoJson) => Id[Coto](json.uuid)
      )
    )
}

object Cotos {
  sealed trait Msg

  case object FetchMoreRecent extends Msg
  case class RecentFetched(
      result: Either[Error, Paginated[CotoJson]]
  ) extends Msg
}
