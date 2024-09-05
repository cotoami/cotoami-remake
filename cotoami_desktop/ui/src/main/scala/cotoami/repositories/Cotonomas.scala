package cotoami.repositories

import com.softwaremill.quicklens._

import fui._
import cotoami.{Msg => AppMsg}
import cotoami.backend._

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,
    mapByCotoId: Map[Id[Coto], Id[Cotonoma]] = Map.empty,

    // Id references
    focusedId: Option[Id[Cotonoma]] = None,
    superIds: Seq[Id[Cotonoma]] = Seq.empty,
    subIds: PaginatedIds[Cotonoma] = PaginatedIds(),
    recentIds: PaginatedIds[Cotonoma] = PaginatedIds()
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

  lazy val supers: Seq[Cotonoma] = this.superIds.map(this.get).flatten

  lazy val subs: Seq[Cotonoma] = this.subIds.order.map(this.get).flatten

  lazy val recent: Seq[Cotonoma] = this.recentIds.order.map(this.get).flatten

  def appendPageOfSubs(page: Paginated[Cotonoma, _]): Cotonomas =
    this
      .putAll(page.rows)
      .modify(_.subIds).using(_.appendPage(page))

  def appendPageOfRecent(page: Paginated[Cotonoma, _]): Cotonomas =
    this
      .putAll(page.rows)
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
