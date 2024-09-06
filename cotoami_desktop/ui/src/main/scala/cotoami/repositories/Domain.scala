package cotoami.repositories

import scala.collection.immutable.HashSet

import com.softwaremill.quicklens._

import fui._
import cotoami.{log_info, Msg => AppMsg}
import cotoami.backend._
import cotoami.components.MapLibre.MarkerDef

case class Domain(
    lastChangeNumber: Double = 0,
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),
    graphLoading: HashSet[Id[Coto]] = HashSet.empty
) {
  def unfocus(): Domain =
    this.copy(
      nodes = this.nodes.focus(None),
      cotonomas = Cotonomas(),
      cotos = Cotos(),
      links = Links()
    )

  def currentRootCotonomaId: Option[Id[Cotonoma]] =
    this.nodes.current.flatMap(_.rootCotonomaId)

  def isCurrentRoot(id: Id[Cotonoma]): Boolean =
    Some(id) == this.currentRootCotonomaId

  def currentCotonomaId: Option[Id[Cotonoma]] =
    this.cotonomas.focusedId.orElse(
      this.nodes.current.flatMap(_.rootCotonomaId)
    )

  def inCurrentRoot: Boolean =
    (this.currentCotonomaId, this.currentRootCotonomaId) match {
      case (Some(current), Some(root)) => current == root
      case _                           => false
    }

  // Note: Even if `currentCotonomaId` has `Some` value, this method will
  // return `None` if the cotonoma data of that ID has not been fetched.
  def currentCotonoma: Option[Cotonoma] =
    this.currentCotonomaId.flatMap(this.cotonomas.get)

  def currentCotonomaCoto: Option[Coto] =
    this.currentCotonoma.flatMap(cotonoma => this.cotos.get(cotonoma.cotoId))

  def isRoot(cotonoma: Cotonoma): Boolean =
    this.nodes.get(cotonoma.nodeId)
      .map(_.rootCotonomaId == Some(cotonoma.id))
      .getOrElse(false)

  def inContext(coto: Coto): Boolean =
    (this.nodes.focusedId, this.cotonomas.focusedId) match {
      case (None, None)          => true
      case (Some(nodeId), None)  => coto.nodeId == nodeId
      case (_, Some(cotonomaId)) => coto.postedInIds.contains(cotonomaId)
    }

  def location: Option[(Node, Option[Cotonoma])] =
    this.nodes.current.map(currentNode =>
      // The location contains a cotonoma only when one is focused,
      // otherwise the root cotonoma of the current node will be implicitly
      // used as the current cotonoma.
      this.cotonomas.focused match {
        case Some(cotonoma) =>
          (
            this.nodes.get(cotonoma.nodeId).getOrElse(currentNode),
            Some(cotonoma)
          )
        case None => (currentNode, None)
      }
    )

  def setCotonomaDetails(details: CotonomaDetails): Domain =
    this
      .modify(_.nodes).using(nodes =>
        if (nodes.focusedId.map(_ != details.cotonoma.nodeId).getOrElse(false))
          nodes.focus(Some(details.cotonoma.nodeId))
        else
          nodes
      )
      .modify(_.cotonomas).using(_.setCotonomaDetails(details))

  def importFrom(cotonomaPair: (Cotonoma, Coto)): Domain =
    this
      .modify(_.cotonomas).using(_.put(cotonomaPair._1))
      .modify(_.cotos).using(_.put(cotonomaPair._2))

  def importFrom(cotos: PaginatedCotos): Domain =
    this
      .modify(_.cotos).using(_.importFrom(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.relatedData))
      .modify(_.links).using(_.putAll(cotos.outgoingLinks))

  def importFrom(cotos: GeolocatedCotos): Domain =
    this
      .modify(_.cotos).using(_.importFrom(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.relatedData))

  def importFrom(graph: CotoGraph): Domain =
    this
      .modify(_.graphLoading).using(_ - graph.rootCotoId)
      .modify(_.cotos).using(_.importFrom(graph))
      .modify(_.cotonomas).using(_.importFrom(graph))
      .modify(_.links).using(_.putAll(graph.links))

  val recentCotonomasWithoutRoot: Seq[Cotonoma] = {
    val rootId = this.currentRootCotonomaId
    this.cotonomas.recent.filter(c => Some(c.id) != rootId)
  }

  val superCotonomasWithoutRoot: Seq[Cotonoma] = {
    val rootId = this.currentRootCotonomaId
    this.cotonomas.supers.filter(c => Some(c.id) != rootId)
  }

  lazy val pinnedCotos: Seq[(Link, Coto)] =
    this.currentCotonoma.map(cotonoma =>
      this.childrenOf(cotonoma.cotoId)
    ).getOrElse(Seq.empty)

  def childrenOf(cotoId: Id[Coto]): Seq[(Link, Coto)] =
    this.links.linksFrom(cotoId).toSeq
      .map(link =>
        this.cotos.get(link.targetCotoId).map(child => (link, child))
      )
      .flatten

  def parentsOf(
      cotoId: Id[Coto],
      excludeCurrentCotonoma: Boolean = true
  ): Seq[(Coto, Link)] =
    this.links.linksTo(cotoId)
      .map(link =>
        this.cotos.get(link.sourceCotoId).flatMap(parent =>
          if (
            excludeCurrentCotonoma &&
            this.currentCotonoma.map(_.cotoId == parent.id)
              .getOrElse(false)
          )
            None
          else
            Some((parent, link))
        )
      )
      .flatten

  def pinned(cotoId: Id[Coto]): Boolean =
    this.currentCotonoma.map(cotonoma =>
      this.links.linked(cotonoma.cotoId, cotoId)
    ).getOrElse(false)

  def fetchCurrentRootCotonoma: Cmd[AppMsg] =
    this.currentRootCotonomaId.map(
      Cotonoma.fetch(_)
        .map(Domain.Msg.toApp(Domain.Msg.CurrentRootCotonomaFetched))
    ).getOrElse(Cmd.none)

  def fetchRecentCotonomas(
      pageIndex: Double
  ): Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    Cotonoma.fetchRecent(this.nodes.focusedId, pageIndex)

  def fetchMoreRecentCotonomas: Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    this.cotonomas.recentIds.nextPageIndex
      .map(fetchRecentCotonomas)
      .getOrElse(Cmd.none)

  def fetchSubCotonomas(
      pageIndex: Double
  ): Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    this.cotonomas.focusedId.map(Cotonoma.fetchSubs(_, pageIndex))
      .getOrElse(Cmd.none)

  def fetchMoreSubCotonomas: Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    this.cotonomas.subIds.nextPageIndex
      .map(fetchSubCotonomas)
      .getOrElse(Cmd.none)

  def fetchGraph: Cmd[AppMsg] =
    this.currentCotonomaId
      .map(Domain.fetchGraphFromCotonoma)
      .getOrElse(Cmd.none)

  // Fetch the graph from the given coto if it has outgoing links that
  // have not yet been loaded (the target cotos of them should also be loaded).
  def lazyFetchGraphFromCoto(cotoId: Id[Coto]): Cmd[AppMsg] =
    this.cotos.get(cotoId).map(coto => {
      if (this.childrenOf(cotoId).size < coto.outgoingLinks)
        Domain.fetchGraphFromCoto(cotoId)
      else
        Cmd.none
    }).getOrElse(Cmd.none)

  lazy val cotoMarkerDefs: Seq[MarkerDef] =
    this.cotos.geolocated.flatMap { case (coto, location) =>
      this.nodes.get(coto.nodeId).map(node =>
        MarkerDef(
          coto.id.uuid,
          location.toLngLat,
          if (coto.isCotonoma)
            node.newCotonomaMarkerHtml(this.inContext(coto))
          else
            node.newCotoMarkerHtml(this.inContext(coto)),
          None,
          coto.nameAsCotonoma
        )
      )
    }
}

object Domain {

  def apply(dataset: InitialDataset, localId: Id[Node]): Domain =
    Domain(
      lastChangeNumber = dataset.lastChangeNumber,
      nodes = Nodes(dataset, localId)
    )

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.DomainMsg(this)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen AppMsg.DomainMsg

    case class CotonomaFetched(result: Either[ErrorJson, (Cotonoma, Coto)])
        extends Msg

    case class CurrentRootCotonomaFetched(
        result: Either[ErrorJson, (Cotonoma, Coto)]
    ) extends Msg

    case class CotonomaDetailsFetched(
        result: Either[ErrorJson, CotonomaDetails]
    ) extends Msg

    case class FetchGraphFromCoto(cotoId: Id[Coto]) extends Msg

    case class CotoGraphFetched(result: Either[ErrorJson, CotoGraph])
        extends Msg
  }

  def update(msg: Msg, model: Domain): (Domain, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.CotonomaFetched(Right(cotonomaPair)) =>
        (
          model.importFrom(cotonomaPair),
          Seq(log_info("Cotonoma fetched.", Some(cotonomaPair._1.name)))
        )

      case Msg.CotonomaFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch a cotonoma.")))

      case Msg.CurrentRootCotonomaFetched(Right(cotonomaPair)) =>
        (
          model.importFrom(cotonomaPair),
          Seq(
            Browser.send(AppMsg.InitCurrentCotonoma(cotonomaPair)),
            log_info("The root cotonoma fetched.", Some(cotonomaPair._1.name))
          )
        )

      case Msg.CurrentRootCotonomaFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch the root cotonoma.")))

      case Msg.CotonomaDetailsFetched(Right(details)) =>
        (
          model.setCotonomaDetails(details),
          Seq(
            Browser.send(
              AppMsg.InitCurrentCotonoma((details.cotonoma, details.coto))
            ),
            log_info("Cotonoma details fetched.", Some(details.debug))
          )
        )

      case Msg.CotonomaDetailsFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch cotonoma details.")))

      case Msg.FetchGraphFromCoto(cotoId) =>
        (
          model.modify(_.graphLoading).using(_ + cotoId),
          Seq(fetchGraphFromCoto(cotoId))
        )

      case Msg.CotoGraphFetched(Right(graph)) =>
        (
          model.importFrom(graph),
          Seq(log_info("Coto graph fetched.", Some(graph.debug)))
        )

      case Msg.CotoGraphFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch a coto graph.")))
    }

  def fetchCotonomaDetails(id: Id[Cotonoma]): Cmd[AppMsg] =
    CotonomaDetails.fetch(id).map(Msg.toApp(Msg.CotonomaDetailsFetched))

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd[AppMsg] =
    CotoGraph.fetchFromCoto(coto).map(Msg.toApp(Msg.CotoGraphFetched))

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd[AppMsg] =
    CotoGraph.fetchFromCotonoma(cotonoma).map(Msg.toApp(Msg.CotoGraphFetched))
}
