package cotoami.repositories

import com.softwaremill.quicklens._

import fui._
import cotoami.{Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Page, PaginatedIds}
import cotoami.backend.{
  CotoGraph,
  CotonomaBackend,
  CotonomaDetails,
  CotosRelatedData
}

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,
    mapByCotoId: Map[Id[Coto], Id[Cotonoma]] = Map.empty,

    // Classified IDs
    focusedId: Option[Id[Cotonoma]] = None,
    superIds: Seq[Id[Cotonoma]] = Seq.empty,
    subIds: PaginatedIds[Cotonoma] = PaginatedIds(),
    recentIds: PaginatedIds[Cotonoma] = PaginatedIds()
) {
  def get(id: Id[Cotonoma]): Option[Cotonoma] = map.get(id)

  def getByCotoId(id: Id[Coto]): Option[Cotonoma] =
    mapByCotoId.get(id).flatMap(get)

  def isEmpty: Boolean = map.isEmpty

  def contains(id: Id[Cotonoma]): Boolean = map.contains(id)

  def put(cotonoma: Cotonoma): Cotonomas = {
    this
      .modify(_.map).using(_ + (cotonoma.id -> cotonoma))
      .modify(_.mapByCotoId).using(_ + (cotonoma.cotoId -> cotonoma.id))
  }

  def putAll(cotonomas: Iterable[Cotonoma]): Cotonomas =
    cotonomas.foldLeft(this)(_ put _)

  def deleteByCotoId(cotoId: Id[Coto]): Cotonomas =
    mapByCotoId.get(cotoId) match {
      case Some(cotonomaId) =>
        copy(
          map = map - cotonomaId,
          mapByCotoId = mapByCotoId - cotoId,
          focusedId =
            if (focusedId == Some(cotonomaId))
              None
            else
              focusedId
        )
      case None => this
    }

  def importFrom(data: CotosRelatedData): Cotonomas =
    this
      .putAll(data.postedIn)
      .putAll(data.asCotonomas)

  def importFrom(graph: CotoGraph): Cotonomas =
    graph.rootCotonoma.map(put).getOrElse(this)
      .importFrom(graph.cotosRelatedData)

  def setCotonomaDetails(details: CotonomaDetails): Cotonomas = {
    this
      .put(details.cotonoma)
      .putAll(details.supers)
      .putAll(details.subs.items)
      .focus(Some(details.cotonoma.id))
      .modify(_.superIds).setTo(details.supers.map(_.id).toSeq)
      .modify(_.subIds).using(_.appendPage(details.subs))
  }

  def asCotonoma(coto: Coto): Option[Cotonoma] =
    if (coto.isCotonoma)
      getByCotoId(coto.repostOfId.getOrElse(coto.id))
    else
      None

  def focus(id: Option[Id[Cotonoma]]): Cotonomas =
    if (id.map(contains(_)).getOrElse(true))
      unfocus.copy(focusedId = id)
    else
      this

  def focusAndFetch(id: Id[Cotonoma]): (Cotonomas, Cmd.One[AppMsg]) =
    (
      unfocus.copy(focusedId = Some(id)),
      CotonomaDetails.fetch(id).map(AppMsg.CotonomaDetailsFetched)
    )

  def unfocus: Cotonomas =
    copy(focusedId = None, superIds = Seq.empty, subIds = PaginatedIds())

  def isFocusing(id: Id[Cotonoma]): Boolean =
    focusedId.map(_ == id).getOrElse(false)

  def focused: Option[Cotonoma] = focusedId.flatMap(get)

  val supers: Seq[Cotonoma] = superIds.map(get).flatten

  val subs: Seq[Cotonoma] = subIds.order.map(get).flatten

  val recent: Seq[Cotonoma] = recentIds.order.map(get).flatten

  def appendPageOfSubs(page: Page[Cotonoma]): Cotonomas =
    this
      .putAll(page.items)
      .modify(_.subIds).using(_.appendPage(page))

  def appendPageOfRecent(page: Page[Cotonoma]): Cotonomas =
    this
      .putAll(page.items)
      .modify(_.recentIds).using(_.appendPage(page))

  def post(cotonoma: Cotonoma, cotonomaCoto: Coto): Cotonomas =
    this
      .put(cotonoma)
      .modify(_.recentIds).using(_.prependId(cotonoma.id))
      .modify(_.subIds).using(subIds =>
        (cotonomaCoto.postedInId, focusedId) match {
          case (Some(postedIn), Some(focused)) if postedIn == focused =>
            subIds.prependId(cotonoma.id)
          case _ => subIds
        }
      )

  def updated(id: Id[Cotonoma]): (Cotonomas, Cmd.One[AppMsg]) =
    (
      this.modify(_.recentIds).using(_.prependId(id)),
      if (!contains(id))
        CotonomaBackend.fetch(id)
          .map(Domain.Msg.CotonomaFetched(_).into)
      else
        Cmd.none
    )

  def posted(coto: Coto): Seq[Cotonoma] =
    coto.postedInIds.map(get).flatten
}
