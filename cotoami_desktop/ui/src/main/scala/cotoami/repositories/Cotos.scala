package cotoami.repositories

import com.softwaremill.quicklens._
import fui.FunctionalUI._
import cotoami.DomainMsg
import cotoami.backend._

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,

    // Timeline
    timelineIds: PaginatedIds[Coto] = PaginatedIds(),
    timelineLoading: Boolean = false,
    query: Option[String] = None
) {
  def get(id: Id[Coto]): Option[Coto] = this.map.get(id)

  def getOriginal(coto: Coto): Coto =
    coto.repostOfId.flatMap(this.get).getOrElse(coto)

  def add(coto: Coto): Cotos = {
    this.modify(_.map).using(_ + (coto.id -> coto))
  }

  def addAll(cotos: Iterable[Coto]): Cotos = cotos.foldLeft(this)(_ add _)

  def importFrom(graph: CotoGraph): Cotos =
    this
      .addAll(graph.cotos)
      .addAll(graph.cotosRelatedData.originals)

  def timeline: Seq[Coto] = this.timelineIds.order.map(this.get).flatten

  def addTimelinePage(cotos: PaginatedCotos): Cotos =
    this
      .addAll(cotos.page.rows)
      .addAll(cotos.relatedData.originals)
      .modify(_.timelineLoading).setTo(false)
      .modify(_.timelineIds).using(_.add(cotos.page))
}

object Cotos {

  sealed trait Msg
  case object FetchMoreTimeline extends Msg
  case class CotoPosted(result: Either[ErrorJson, CotoJson]) extends Msg

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
              Seq(fetchTimeline(nodeId, cotonomaId, model.query, i))
            )
          ).getOrElse((model, Seq.empty))
        }

      case CotoPosted(Right(coto)) =>
        (
          model,
          Seq(
            cotoami.log_info(
              "CotoPosted",
              Some(scala.scalajs.js.JSON.stringify(coto))
            )
          )
        )

      case CotoPosted(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't post a coto.")))
    }

  def fetchTimeline(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      query: Option[String],
      pageIndex: Double
  ): Cmd[cotoami.Msg] =
    query.map(query =>
      Commands.send(Commands.SearchCotos(query, nodeId, cotonomaId, pageIndex))
    ).getOrElse(
      Commands.send(Commands.RecentCotos(nodeId, cotonomaId, pageIndex))
    ).map(
      Domain.TimelineFetched andThen DomainMsg
    )

  def postCoto(
      content: String,
      summary: Option[String],
      post_to: Id[Cotonoma]
  ): Cmd[cotoami.Msg] =
    Commands.send(Commands.PostCoto(content, summary, post_to)).map(
      CotoPosted andThen Domain.CotosMsg andThen DomainMsg
    )
}
