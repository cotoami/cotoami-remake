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
    timelineLoading: Boolean = false,
    query: Option[String] = None,

    // Coto/Cotonoma inputs waiting to be posted
    waitingPosts: Seq[WaitingPost] = Seq.empty
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

  def appendTimelinePage(cotos: PaginatedCotos): Cotos =
    this
      .addAll(cotos.page.rows)
      .addAll(cotos.relatedData.originals)
      .modify(_.timelineLoading).setTo(false)
      .modify(_.timelineIds).using(_.appendPage(cotos.page))

  def prependToTimeline(coto: Coto): Cotos =
    this
      .add(coto)
      .modify(_.timelineIds).using(timeline =>
        if (this.query.isEmpty)
          timeline.prependId(coto.id)
        else
          timeline
      )

  def addWaitingPost(post: WaitingPost): Cotos =
    this.modify(_.waitingPosts).using(post +: _)

  def removeWaitingPost(postId: String): Cotos =
    this.modify(_.waitingPosts).using(_.filterNot(_.postId == postId))
}

object Cotos {

  sealed trait Msg
  case object FetchMoreTimeline extends Msg
  case class CotoPosted(postId: String, result: Either[ErrorJson, CotoJson])
      extends Msg
  case class CotonomaPosted(
      postId: String,
      result: Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]
  ) extends Msg

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
              Seq(Domain.fetchTimeline(nodeId, cotonomaId, model.query, i))
            )
          ).getOrElse((model, Seq.empty))
        }

      case CotoPosted(postId, Right(cotoJson)) =>
        (
          model.removeWaitingPost(postId),
          Seq(cotoami.log_info("Coto posted.", Some(cotoJson.uuid)))
        )

      case CotoPosted(postId, Left(e)) =>
        (
          model.removeWaitingPost(postId),
          Seq(ErrorJson.log(e, "Couldn't post a coto."))
        )

      case CotonomaPosted(postId, Right(cotonoma)) =>
        (
          model.removeWaitingPost(postId),
          Seq(
            cotoami.log_info(
              "Cotonoma posted.",
              Some(js.JSON.stringify(cotonoma._1))
            )
          )
        )

      case CotonomaPosted(postId, Left(e)) =>
        (
          model.removeWaitingPost(postId),
          Seq(ErrorJson.log(e, "Couldn't post a cotonoma."))
        )
    }

  def postCoto(
      postId: String,
      content: String,
      summary: Option[String],
      post_to: Id[Cotonoma]
  ): Cmd[cotoami.Msg] =
    Commands.send(Commands.PostCoto(content, summary, post_to)).map(
      (CotoPosted(postId, _)) andThen Domain.CotosMsg andThen DomainMsg
    )

  def postCotonoma(
      postId: String,
      name: String,
      post_to: Id[Cotonoma]
  ): Cmd[cotoami.Msg] =
    Commands.send(Commands.PostCotonoma(name, post_to)).map(
      (CotonomaPosted(postId, _)) andThen Domain.CotosMsg andThen DomainMsg
    )
}

case class WaitingPost(
    postId: String,
    content: Option[String],
    summary: Option[String],
    isCotonoma: Boolean,
    postedIn: Cotonoma
) extends CotoContent
