package cotoami.repositories

import scala.util.chaining._
import scala.scalajs.js
import com.softwaremill.quicklens._

import fui._
import cotoami.{Msg => AppMsg}
import cotoami.backend._

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,
    mapByCotoId: Map[Id[Coto], Id[Cotonoma]] = Map.empty,

    // The currently focused cotonoma and its super/sub cotonomas
    focusedId: Option[Id[Cotonoma]] = None,
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

  def put(cotonoma: Cotonoma): Cotonomas = {
    this
      .modify(_.map).using(_ + (cotonoma.id -> cotonoma))
      .modify(_.mapByCotoId).using(_ + (cotonoma.cotoId -> cotonoma.id))
  }

  def putAll(cotonomas: Iterable[Cotonoma]): Cotonomas =
    cotonomas.foldLeft(this)(_ put _)

  def importFrom(data: CotosRelatedData): Cotonomas =
    this
      .putAll(data.postedIn)
      .putAll(data.asCotonomas)

  def importFrom(graph: CotoGraph): Cotonomas =
    graph.rootCotonoma.map(this.put).getOrElse(this)
      .importFrom(graph.cotosRelatedData)

  def setCotonomaDetails(details: CotonomaDetails): Cotonomas = {
    this
      .put(details.cotonoma)
      .putAll(details.supers)
      .putAll(details.subs.rows)
      .focus(Some(details.cotonoma.id))
      .modify(_.superIds).setTo(details.supers.map(_.id).toSeq)
      .modify(_.subIds).using(_.appendPage(details.subs))
  }

  def asCotonoma(coto: Coto): Option[Cotonoma] =
    if (coto.isCotonoma)
      this.getByCotoId(coto.repostOfId.getOrElse(coto.id))
    else
      None

  def focus(id: Option[Id[Cotonoma]]): Cotonomas =
    if (id.map(this.contains(_)).getOrElse(true))
      this.unfocus.copy(focusedId = id)
    else
      this

  def focusAndFetch(id: Id[Cotonoma]): (Cotonomas, Cmd[AppMsg]) =
    (
      this.unfocus.copy(focusedId = Some(id)),
      Domain.fetchCotonomaDetails(id)
    )

  def unfocus: Cotonomas =
    this.copy(focusedId = None, superIds = Seq.empty, subIds = PaginatedIds())

  def isFocusing(id: Id[Cotonoma]): Boolean =
    this.focusedId.map(_ == id).getOrElse(false)

  def focused: Option[Cotonoma] = this.focusedId.flatMap(this.get)

  def supers: Seq[Cotonoma] = this.superIds.map(this.get).flatten

  def subs: Seq[Cotonoma] = this.subIds.order.map(this.get).flatten

  def appendPageOfSubs(page: Paginated[Cotonoma, _]): Cotonomas =
    this
      .putAll(page.rows)
      .modify(_.subsLoading).setTo(false)
      .modify(_.subIds).using(_.appendPage(page))

  def recent: Seq[Cotonoma] = this.recentIds.order.map(this.get).flatten

  def appendPageOfRecent(page: Paginated[Cotonoma, _]): Cotonomas =
    this
      .putAll(page.rows)
      .modify(_.recentLoading).setTo(false)
      .modify(_.recentIds).using(_.appendPage(page))

  def post(cotonoma: Cotonoma, cotonomaCoto: Coto): Cotonomas =
    this
      .put(cotonoma)
      .modify(_.recentIds).using(_.prependId(cotonoma.id))
      .modify(_.subIds).using(subIds =>
        (cotonomaCoto.postedInId, this.focusedId) match {
          case (Some(postedIn), Some(focused)) if postedIn == focused =>
            subIds.prependId(cotonoma.id)
          case _ => subIds
        }
      )

  def updated(id: Id[Cotonoma]): (Cotonomas, Cmd[AppMsg]) =
    (
      this.modify(_.recentIds).using(_.prependId(id)),
      if (!this.contains(id))
        Cotonoma.fetch(id)
          .map(Domain.Msg.toApp(Domain.Msg.CotonomaFetched))
      else
        Cmd.none
    )
}

object Cotonomas {
  def toMap(jsons: js.Array[CotonomaJson]): Map[Id[Cotonoma], Cotonoma] =
    jsons.map(json => (Id[Cotonoma](json.uuid), Cotonoma(json))).toMap

  sealed trait Msg {
    def toApp: AppMsg =
      Domain.Msg.CotonomasMsg(this).pipe(AppMsg.DomainMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Domain.Msg.CotonomasMsg andThen AppMsg.DomainMsg

    case object FetchMoreRecent extends Msg
    case class RecentFetched(result: Either[ErrorJson, Paginated[Cotonoma, _]])
        extends Msg
    case class FetchMoreSubs(id: Id[Cotonoma]) extends Msg
    case class SubsFetched(result: Either[ErrorJson, Paginated[Cotonoma, _]])
        extends Msg
  }

  def update(
      msg: Msg,
      model: Cotonomas,
      nodeId: Option[Id[Node]]
  ): (Cotonomas, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.FetchMoreRecent =>
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

      case Msg.RecentFetched(Right(page)) =>
        (model.appendPageOfRecent(page), Seq.empty)

      case Msg.RecentFetched(Left(e)) =>
        (
          model.copy(recentLoading = false),
          Seq(ErrorJson.log(e, "Couldn't fetch recent cotonomas."))
        )

      case Msg.FetchMoreSubs(id) =>
        if (model.subsLoading) {
          (model, Seq.empty)
        } else {
          model.subIds.nextPageIndex.map(i =>
            (
              model.copy(subsLoading = true),
              Seq(Cotonoma.fetchSubs(id, i).map(Msg.toApp(Msg.SubsFetched)))
            )
          ).getOrElse((model, Seq.empty))
        }

      case Msg.SubsFetched(Right(page)) =>
        (model.appendPageOfSubs(page), Seq.empty)

      case Msg.SubsFetched(Left(e)) =>
        (
          model.copy(subsLoading = false),
          Seq(ErrorJson.log(e, "Couldn't fetch sub cotonomas."))
        )
    }

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[AppMsg] =
    Cotonoma.fetchRecent(nodeId, pageIndex)
      .map(Msg.toApp(Msg.RecentFetched))
}
