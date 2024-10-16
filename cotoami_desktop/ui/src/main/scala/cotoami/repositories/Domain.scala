package cotoami.repositories

import scala.util.chaining._
import scala.collection.immutable.HashSet

import com.softwaremill.quicklens._

import fui._
import cotoami.{Into, Msg => AppMsg}
import cotoami.models.{
  CenterOrBounds,
  Coto,
  Cotonoma,
  Geolocation,
  Id,
  Link,
  Node,
  Page
}
import cotoami.backend.{
  CotoGraph,
  CotonomaBackend,
  CotonomaDetails,
  CotosPage,
  ErrorJson,
  GeolocatedCotos,
  InitialDataset,
  NodeDetails
}

case class Domain(
    lastChangeNumber: Double = 0,
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),
    graphLoading: HashSet[Id[Coto]] = HashSet.empty
) {
  /////////////////////////////////////////////////////////////////////////////
  // Focus
  /////////////////////////////////////////////////////////////////////////////

  def currentFocus: Option[(Node, Option[Cotonoma])] =
    (nodes.focused, cotonomas.focused) match {
      case (None, None)       => None
      case (Some(node), None) => Some((node, None))
      case (_, Some(cotonoma)) =>
        nodes.get(cotonoma.nodeId) match {
          case Some(node) => Some((node, Some(cotonoma)))
          case None       => None // should be unreachable
        }
    }

  def focusedCotonoma: Option[(Cotonoma, Coto)] =
    cotonomas.focused.flatMap(cotonoma =>
      cotos.get(cotonoma.cotoId).map(cotonoma -> _)
    )

  def unfocus(): Domain =
    copy(
      nodes = nodes.focus(None),
      cotonomas = Cotonomas(),
      cotos = cotos.destroyAndCreate(),
      links = Links()
    )

  /////////////////////////////////////////////////////////////////////////////
  // Node root cotonoma
  /////////////////////////////////////////////////////////////////////////////

  def currentNodeRootCotonomaId: Option[Id[Cotonoma]] =
    nodes.current.flatMap(_.rootCotonomaId)

  def currentNodeRoot: Option[(Cotonoma, Coto)] =
    currentNodeRootCotonomaId.flatMap(cotonomaPair)

  def isCurrentNodeRoot(id: Id[Cotonoma]): Boolean =
    Some(id) == currentNodeRootCotonomaId

  def isNodeRoot(cotonoma: Cotonoma): Boolean =
    nodes.get(cotonoma.nodeId)
      .map(_.rootCotonomaId == Some(cotonoma.id))
      .getOrElse(false)

  def rootOf(nodeId: Id[Node]): Option[(Cotonoma, Coto)] =
    nodes.get(nodeId).flatMap(_.rootCotonomaId.flatMap(cotonomaPair))

  /////////////////////////////////////////////////////////////////////////////
  // Current cotonoma
  /////////////////////////////////////////////////////////////////////////////

  def currentCotonomaId: Option[Id[Cotonoma]] =
    cotonomas.focusedId.orElse(
      nodes.current.flatMap(_.rootCotonomaId)
    )

  // Note: Even if `currentCotonomaId` has `Some` value, this method will
  // return `None` if the cotonoma data of that ID has not been fetched.
  def currentCotonoma: Option[Cotonoma] =
    currentCotonomaId.flatMap(cotonomas.get)

  /////////////////////////////////////////////////////////////////////////////
  // Other queries
  /////////////////////////////////////////////////////////////////////////////

  def cotonomaPair(id: Id[Cotonoma]): Option[(Cotonoma, Coto)] =
    cotonomas.get(id).flatMap(cotonoma =>
      cotos.get(cotonoma.cotoId).map(cotonoma -> _)
    )

  val recentCotonomasWithoutRoot: Seq[Cotonoma] = {
    cotonomas.recent.filter(c => Some(c.id) != currentNodeRootCotonomaId)
  }

  val superCotonomasWithoutRoot: Seq[Cotonoma] = {
    cotonomas.supers.filter(c => Some(c.id) != currentNodeRootCotonomaId)
  }

  lazy val pinnedCotos: Seq[(Link, Coto)] =
    currentCotonoma.map(cotonoma => childrenOf(cotonoma.cotoId))
      .getOrElse(Seq.empty)

  def childrenOf(cotoId: Id[Coto]): Seq[(Link, Coto)] =
    links.linksFrom(cotoId).toSeq
      .map(link => cotos.get(link.targetCotoId).map(child => (link, child)))
      .flatten

  def parentsOf(
      cotoId: Id[Coto],
      excludeCurrentCotonoma: Boolean = true
  ): Seq[(Coto, Link)] =
    links.linksTo(cotoId)
      .map(link =>
        cotos.get(link.sourceCotoId).flatMap(parent =>
          if (
            excludeCurrentCotonoma &&
            currentCotonoma.map(_.cotoId == parent.id)
              .getOrElse(false)
          )
            None
          else
            Some((parent, link))
        )
      )
      .flatten

  def pinned(cotoId: Id[Coto]): Boolean =
    currentCotonoma.map(cotonoma =>
      links.linked(cotonoma.cotoId, cotoId)
    ).getOrElse(false)

  lazy val geolocationInFocus: Option[CenterOrBounds] =
    cotos.focused.flatMap(_.geolocation) match {
      case Some(cotoLocation) => Some(Left(cotoLocation))
      case None =>
        focusedCotonoma.map(_._2).flatMap(_.geolocation) match {
          case Some(center) => Some(Left(center))
          case None => {
            val cotos = this.cotos.geolocated.map(_._1).filter(inFocus)
            Coto.centerOrBoundsOf(cotos)
          }
        }
    }

  lazy val locationMarkers: Seq[Geolocation.MarkerOfCotos] = {
    var markers: Map[Geolocation, Geolocation.MarkerOfCotos] = Map.empty
    cotos.geolocated.foreach { case (coto, location) =>
      nodes.get(coto.nodeId).foreach(node =>
        markers = markers.updatedWith(location) {
          case Some(marker) =>
            Some(marker.addCoto(coto, node.iconUrl, inFocus(coto)))
          case None =>
            Some(
              Geolocation.MarkerOfCotos(
                location,
                Seq(coto),
                Set(node.iconUrl),
                inFocus(coto)
              )
            )
        }
      )
    }
    markers.values.toSeq
  }

  def canPost: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.canPostTo(cotonoma.nodeId)
      case None           => false
    }

  def canEditLinks: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.canEditLinksIn(cotonoma.nodeId)
      case None           => false
    }

  /////////////////////////////////////////////////////////////////////////////
  // Import
  /////////////////////////////////////////////////////////////////////////////

  def setCotonomaDetails(details: CotonomaDetails): Domain =
    this
      .modify(_.nodes).using(nodes =>
        if (nodes.focusedId.map(_ != details.cotonoma.nodeId).getOrElse(false))
          nodes.focus(Some(details.cotonoma.nodeId))
        else
          nodes
      )
      .modify(_.cotonomas).using(_.setCotonomaDetails(details))
      .modify(_.cotos).using(_.put(details.coto))

  def importFrom(cotonomaPair: (Cotonoma, Coto)): Domain =
    this
      .modify(_.cotonomas).using(_.put(cotonomaPair._1))
      .modify(_.cotos).using(_.put(cotonomaPair._2))

  def importFrom(cotos: CotosPage): Domain =
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

  /////////////////////////////////////////////////////////////////////////////
  // Commands
  /////////////////////////////////////////////////////////////////////////////

  def fetchRecentCotonomas(
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, Page[Cotonoma]]] =
    CotonomaBackend.fetchRecent(nodes.focusedId, pageIndex)

  def fetchMoreRecentCotonomas: Cmd.One[Either[ErrorJson, Page[Cotonoma]]] =
    cotonomas.recentIds.nextPageIndex
      .map(fetchRecentCotonomas)
      .getOrElse(Cmd.none)

  def fetchSubCotonomas(
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, Page[Cotonoma]]] =
    cotonomas.focusedId.map(CotonomaBackend.fetchSubs(_, pageIndex))
      .getOrElse(Cmd.none)

  def fetchMoreSubCotonomas: Cmd.One[Either[ErrorJson, Page[Cotonoma]]] =
    cotonomas.subIds.nextPageIndex
      .map(fetchSubCotonomas)
      .getOrElse(Cmd.none)

  def fetchGraph: Cmd.One[AppMsg] =
    currentCotonomaId
      .map(Domain.fetchGraphFromCotonoma)
      .getOrElse(Cmd.none)

  // Fetch the graph from the given coto if it has outgoing links that
  // have not yet been loaded (the target cotos of them should also be loaded).
  def lazyFetchGraphFromCoto(cotoId: Id[Coto]): Cmd.One[AppMsg] =
    cotos.get(cotoId).map(coto => {
      if (childrenOf(cotoId).size < coto.outgoingLinks)
        Domain.fetchGraphFromCoto(cotoId)
      else
        Cmd.none
    }).getOrElse(Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // Private
  /////////////////////////////////////////////////////////////////////////////

  private def inFocus(coto: Coto): Boolean =
    (nodes.focusedId, cotonomas.focusedId) match {
      case (None, None)         => true
      case (Some(nodeId), None) => coto.nodeId == nodeId
      case (_, Some(cotonomaId)) =>
        coto.postedInIds.contains(cotonomaId) || (
          // if the coto is the current cotonoma itself
          cotonomas.getByCotoId(coto.id).map(_.id) == Some(cotonomaId)
        )
    }
}

object Domain {

  def apply(dataset: InitialDataset, localId: Id[Node]): Domain =
    Domain(
      lastChangeNumber = dataset.lastChangeNumber,
      nodes = Nodes(dataset, localId)
    )

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.DomainMsg(this)
  }

  object Msg {
    case class NodeDetailsFetched(result: Either[ErrorJson, NodeDetails])
        extends Msg
    case class CotonomaFetched(result: Either[ErrorJson, (Cotonoma, Coto)])
        extends Msg
    case class FetchGraphFromCoto(cotoId: Id[Coto]) extends Msg
    case class CotoGraphFetched(result: Either[ErrorJson, CotoGraph])
        extends Msg
  }

  def update(msg: Msg, model: Domain): (Domain, Cmd[AppMsg]) =
    msg match {
      case Msg.NodeDetailsFetched(Right(details)) =>
        (
          model.modify(_.nodes).using(_.put(details.node)).pipe { model =>
            details.root match {
              case Some((cotonoma, coto)) =>
                model
                  .modify(_.cotonomas).using(_.put(cotonoma))
                  .modify(_.cotos).using(_.put(coto))
              case None => model
            }
          },
          Cmd.none
        )

      case Msg.NodeDetailsFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch node details.", e))

      case Msg.CotonomaFetched(Right(cotonomaPair)) =>
        (model.importFrom(cotonomaPair), Cmd.none)

      case Msg.CotonomaFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch a cotonoma.", e))

      case Msg.FetchGraphFromCoto(cotoId) =>
        (
          model.modify(_.graphLoading).using(_ + cotoId),
          fetchGraphFromCoto(cotoId)
        )

      case Msg.CotoGraphFetched(Right(graph)) =>
        (model.importFrom(graph), Cmd.none)

      case Msg.CotoGraphFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch a coto graph.", e))
    }

  def fetchNodeDetails(id: Id[Node]): Cmd.One[AppMsg] =
    NodeDetails.fetch(id).map(Msg.NodeDetailsFetched(_).into)

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd.One[AppMsg] =
    CotoGraph.fetchFromCoto(coto).map(Msg.CotoGraphFetched(_).into)

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd.One[AppMsg] =
    CotoGraph.fetchFromCotonoma(cotonoma).map(Msg.CotoGraphFetched(_).into)
}
