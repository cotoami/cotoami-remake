package cotoami.repositories

import fui.FunctionalUI._
import cotoami.{node_command, TimelineFetched}
import cotoami.backend._

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,

    // Timeline
    timelineIds: PaginatedIds[Coto] = PaginatedIds(),
    timelineLoading: Boolean = false
) {
  def get(id: Id[Coto]): Option[Coto] = this.map.get(id)

  def getOriginal(coto: Coto): Coto =
    coto.repostOfId.flatMap(this.get).getOrElse(coto)

  def timeline: Seq[Coto] = this.timelineIds.order.map(this.get).flatten

  def appendTimeline(cotos: PaginatedCotosJson): Cotos =
    this.copy(
      map = this.map ++
        Coto.toMap(cotos.page.rows) ++
        Coto.toMap(cotos.originals),
      timelineLoading = false,
      timelineIds = this.timelineIds.addPage(
        cotos.page,
        (json: CotoJson) => Id[Coto](json.uuid)
      )
    )
}

object Cotos {
  sealed trait Msg

  case object FetchMoreTimeline extends Msg

  def update(
      msg: Msg,
      model: Cotos,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]]
  ): (Cotos, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case FetchMoreTimeline =>
        if (model.timelineLoading) {
          (model, Seq.empty)
        } else {
          model.timelineIds.nextPageIndex.map(i =>
            (
              model.copy(timelineLoading = true),
              Seq(fetchTimeline(nodeId, cotonomaId, i))
            )
          ).getOrElse((model, Seq.empty))
        }
    }

  def fetchTimeline(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd[cotoami.Msg] =
    node_command(Commands.RecentCotos(nodeId, cotonomaId, pageIndex)).map(
      TimelineFetched
    )
}
