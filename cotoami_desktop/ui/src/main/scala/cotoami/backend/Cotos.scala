package cotoami.backend

import scala.scalajs.js
import fui.FunctionalUI._
import cotoami.{node_command, Id, TimelineFetched}

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,

    // Timeline
    timelineIds: PaginatedIds[Coto] = PaginatedIds(),
    timelineLoading: Boolean = false
) {
  def get(id: Id[Coto]): Option[Coto] = this.map.get(id)

  def timeline: Seq[Coto] = this.timelineIds.order.map(this.get).flatten

  def appendTimeline(cotos: CotosJson): Cotos =
    this.copy(
      map = this.map ++
        Coto.toMap(cotos.paginated.rows) ++
        Coto.toMap(cotos.repost_of),
      timelineLoading = false,
      timelineIds = this.timelineIds.addPage(
        cotos.paginated,
        (json: CotoJson) => Id[Coto](json.uuid)
      )
    )
}

object Cotos {
  sealed trait Msg

  case object FetchMoreTimeline extends Msg

  def fetchTimeline(
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd[cotoami.Msg] =
    node_command(Commands.RecentCotos(cotonomaId, pageIndex)).map(
      TimelineFetched
    )
}

@js.native
trait CotosJson extends js.Object {
  val paginated: Paginated[CotoJson] = js.native
  val posted_in: js.Array[CotonomaJson] = js.native
  val repost_of: js.Array[CotoJson] = js.native
}
