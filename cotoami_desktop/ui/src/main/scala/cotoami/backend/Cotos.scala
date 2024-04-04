package cotoami.backend

import scala.scalajs.js
import cotoami.Id

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
}

@js.native
trait CotosJson extends js.Object {
  val paginated: Paginated[CotoJson] = js.native
  val posted_in: js.Array[CotonomaJson] = js.native
  val repost_of: js.Array[CotoJson] = js.native
}
