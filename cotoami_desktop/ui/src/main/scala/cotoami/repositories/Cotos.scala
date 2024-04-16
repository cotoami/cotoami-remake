package cotoami.repositories

import scala.scalajs.js
import com.softwaremill.quicklens._
import fui.FunctionalUI._
import cotoami.DomainMsg
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

  def add(json: CotoJson): Cotos = {
    val coto = Coto(json)
    this.modify(_.map).using(_ + (coto.id -> coto))
  }

  def addAll(jsons: js.Array[CotoJson]): Cotos =
    jsons.foldLeft(this)(_ add _)

  def importFrom(graph: CotoGraphJson): Cotos =
    this
      .addAll(graph.cotos)
      .addAll(graph.cotos_related_data.originals)

  def timeline: Seq[Coto] = this.timelineIds.order.map(this.get).flatten

  def appendTimeline(cotos: PaginatedCotosJson): Cotos =
    this
      .addAll(cotos.page.rows)
      .addAll(cotos.related_data.originals)
      .modify(_.timelineLoading).setTo(false)
      .modify(_.timelineIds).using(
        _.addPage(
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
    Commands.send(Commands.RecentCotos(nodeId, cotonomaId, pageIndex)).map(
      Domain.TimelineFetched andThen DomainMsg
    )
}
