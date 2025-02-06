package cotoami.repositories

import scala.util.chaining._
import scala.collection.immutable.HashSet
import scala.scalajs.js

import com.softwaremill.quicklens._

import fui._
import cotoami.{Into, Msg => AppMsg}
import cotoami.models._
import cotoami.backend._

case class Domain(
    lastChangeNumber: Double = 0,
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),

    // processing state
    graphLoading: HashSet[Id[Coto]] = HashSet.empty,
    graphLoaded: HashSet[Id[Coto]] = HashSet.empty,
    deleting: HashSet[Id[Coto]] = HashSet.empty,
    pinning: HashSet[Id[Coto]] = HashSet.empty,
    reordering: HashSet[Id[Coto]] = HashSet.empty
) {
  def onFocusChange: Domain =
    copy(
      graphLoading = HashSet.empty,
      graphLoaded = HashSet.empty,
      deleting = HashSet.empty,
      pinning = HashSet.empty,
      reordering = HashSet.empty
    )

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

  def unfocus: Domain =
    onFocusChange.copy(
      nodes = nodes.focus(None),
      cotonomas = Cotonomas(),
      cotos = cotos.clear(),
      links = Links()
    )

  /////////////////////////////////////////////////////////////////////////////
  // Cotos
  ///////////////////////////////////////////////////////////////////////////

  def deleteCoto(id: Id[Coto]): Domain = {
    // Delete the reposts first if they exist
    cotos.repostsOf(id).foldLeft(this)(_ deleteCoto _.id)
      // then, delete the specified coto (which could be a cotonoma)
      // and the links to/from the coto.
      .modify(_.cotos).using(_.delete(id))
      .modify(_.cotonomas).using(_.deleteByCotoId(id))
      .modify(_.links).using(_.onCotoDelete(id))
  }

  def beingDeleted(cotoId: Id[Coto]): Boolean = deleting.contains(cotoId)

  def beingPinned(cotoId: Id[Coto]): Boolean = pinning.contains(cotoId)

  /////////////////////////////////////////////////////////////////////////////
  // Cotonomas
  /////////////////////////////////////////////////////////////////////////////

  def cotonoma(id: Id[Cotonoma]): Option[(Cotonoma, Coto)] =
    cotonomas.get(id).flatMap(cotonoma =>
      cotos.get(cotonoma.cotoId).map(cotonoma -> _)
    )

  def currentCotonomaId: Option[Id[Cotonoma]] =
    cotonomas.focusedId.orElse(
      nodes.current.flatMap(_.rootCotonomaId)
    )

  // Note: Even if `currentCotonomaId` has `Some` value, this method will
  // return `None` if the cotonoma object has not been loaded.
  def currentCotonoma: Option[Cotonoma] =
    currentCotonomaId.flatMap(cotonomas.get)

  def currentCotonomaPair: Option[(Cotonoma, Coto)] =
    currentCotonomaId.flatMap(cotonoma)

  def currentNodeRoot: Option[(Cotonoma, Coto)] =
    nodes.currentNodeRootCotonomaId.flatMap(cotonoma)

  def currentNodeRootCotonoma: Option[Cotonoma] =
    nodes.currentNodeRootCotonomaId.flatMap(cotonomas.get)

  def rootOf(nodeId: Id[Node]): Option[(Cotonoma, Coto)] =
    nodes.get(nodeId).flatMap(_.rootCotonomaId.flatMap(cotonoma))

  val recentCotonomasWithoutRoot: Seq[Cotonoma] = {
    cotonomas.recent.filter(c => Some(c.id) != nodes.currentNodeRootCotonomaId)
  }

  val superCotonomasWithoutRoot: Seq[Cotonoma] = {
    cotonomas.supers.filter(c => Some(c.id) != nodes.currentNodeRootCotonomaId)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Links
  /////////////////////////////////////////////////////////////////////////////

  lazy val pins: Siblings =
    currentCotonoma.map(cotonoma => childrenOf(cotonoma.cotoId))
      .getOrElse(Siblings.empty)

  def childrenOf(cotoId: Id[Coto]): Siblings =
    links.from(cotoId).toSeq
      .map(link => cotos.get(link.targetCotoId).map(child => (link, child)))
      .flatten
      .pipe(Siblings(_))

  def parentsOf(
      cotoId: Id[Coto],
      excludeCurrentCotonoma: Boolean = true
  ): Seq[(Coto, Link)] =
    links.to(cotoId)
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

  def isPin(link: Link): Boolean =
    currentCotonoma.map(link.sourceCotoId == _.cotoId).getOrElse(false)

  def pinned(cotoId: Id[Coto]): Boolean =
    currentCotonoma.map(cotonoma =>
      links.linked(cotonoma.cotoId, cotoId)
    ).getOrElse(false)

  /////////////////////////////////////////////////////////////////////////////
  // Graph
  /////////////////////////////////////////////////////////////////////////////

  def alreadyLoadedGraphFrom(cotoId: Id[Coto]): Boolean =
    graphLoaded.contains(cotoId)

  def anyTargetMissingLinksFrom(id: Id[Coto]): Boolean =
    links.from(id).find(link => !cotos.contains(link.targetCotoId)).isDefined

  /////////////////////////////////////////////////////////////////////////////
  // Geolocation
  /////////////////////////////////////////////////////////////////////////////

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

  /////////////////////////////////////////////////////////////////////////////
  // Permission check
  /////////////////////////////////////////////////////////////////////////////

  def canPost: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.canPostTo(cotonoma.nodeId)
      case None           => false
    }

  def canCreateLinks: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.canCreateLinksIn(cotonoma.nodeId)
      case None           => false
    }

  def canRepost(cotoId: Id[Coto]): Boolean =
    // You can't repost the current node root.
    !Seq(
      currentNodeRoot.map(_._2.id)
    ).flatten.contains(cotoId)

  def canPin(cotoId: Id[Coto]): Boolean =
    canCreateLinks && !pinned(cotoId) &&
      // You can't pin the current cotonoma (obviously) and the current node root.
      !Seq(
        currentCotonomaPair.map(_._2.id),
        currentNodeRoot.map(_._2.id)
      ).flatten.contains(cotoId)

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

  def importFrom(details: CotoDetails): Domain =
    this
      .modify(_.cotos).using(_.put(details.coto))
      .modify(_.cotonomas).using(_.importFrom(details.relatedData))
      .modify(_.links).using(_.putAll(details.outgoingLinks))

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
      .modify(_.graphLoaded).using(_ + graph.rootCotoId)
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

  def lazyFetchGraphFrom(cotoId: Id[Coto]): Cmd.One[AppMsg] =
    if (
      !alreadyLoadedGraphFrom(cotoId) &&
      (
        cotos.isCotonoma(cotoId).getOrElse(false) ||
          anyTargetMissingLinksFrom(cotoId)
      )
    )
      Domain.fetchGraphFromCoto(cotoId)
    else
      Cmd.none

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
    case class CotoDetailsFetched(result: Either[ErrorJson, CotoDetails])
        extends Msg
    case class LinkFetched(result: Either[ErrorJson, Link]) extends Msg
    case class FetchGraphFromCoto(cotoId: Id[Coto]) extends Msg
    case class CotoGraphFetched(result: Either[ErrorJson, CotoGraph])
        extends Msg
    case class DeleteCoto(id: Id[Coto]) extends Msg
    case class CotoDeleted(id: Id[Coto], result: Either[ErrorJson, Id[Coto]])
        extends Msg
    case class Pin(cotoId: Id[Coto]) extends Msg
    case class Pinned(cotoId: Id[Coto], result: Either[ErrorJson, Link])
        extends Msg
    case class ChangeOrder(link: Link, newOrder: Int) extends Msg
    case class OrderChanged(
        sourceCotoId: Id[Coto],
        result: Either[ErrorJson, Link]
    ) extends Msg
    case class OutgoingLinksFetched(
        cotoId: Id[Coto],
        result: Either[ErrorJson, js.Array[Link]]
    ) extends Msg
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

      case Msg.CotoDetailsFetched(Right(details)) =>
        (model.importFrom(details), Cmd.none)

      case Msg.CotoDetailsFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch coto details.", e))

      case Msg.LinkFetched(Right(link)) =>
        (model.modify(_.links).using(_.put(link)), Cmd.none)

      case Msg.LinkFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch a link.", e))

      case Msg.FetchGraphFromCoto(cotoId) =>
        (
          model.modify(_.graphLoading).using(_ + cotoId),
          fetchGraphFromCoto(cotoId)
        )

      case Msg.CotoGraphFetched(Right(graph)) =>
        (
          model
            .modify(_.graphLoading).using(_ - graph.rootCotoId)
            .importFrom(graph),
          Cmd.none
        )

      case Msg.CotoGraphFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch a coto graph.", e))

      case Msg.DeleteCoto(id) =>
        (
          model.modify(_.deleting).using(_ + id),
          deleteCoto(id)
        )

      case Msg.CotoDeleted(id, Right(_)) =>
        (
          model.modify(_.deleting).using(_ - id),
          Cmd.none
        )

      case Msg.CotoDeleted(id, Left(e)) =>
        (
          model.modify(_.deleting).using(_ - id),
          cotoami.error("Couldn't delete a coto.", e)
        )

      case Msg.Pin(cotoId) =>
        model.currentCotonoma.map(cotonoma =>
          (
            model.modify(_.pinning).using(_ + cotoId),
            LinkBackend.connect(
              cotonoma.cotoId,
              cotoId,
              None,
              None,
              None
            )
              .map(Msg.Pinned(cotoId, _).into)
          )
        ).getOrElse((model, Cmd.none))

      case Msg.Pinned(cotoId, Right(_)) =>
        (
          model.modify(_.pinning).using(_ - cotoId),
          Cmd.none
        )

      case Msg.Pinned(cotoId, Left(e)) =>
        (
          model.modify(_.pinning).using(_ - cotoId),
          cotoami.error("Couldn't pin a coto.", e)
        )

      case Msg.ChangeOrder(link, newOrder) =>
        (
          model.modify(_.reordering).using(_ + link.sourceCotoId),
          changeOrder(link, newOrder)
        )

      case Msg.OrderChanged(sourceCotoId, Right(_)) =>
        (
          model.modify(_.reordering).using(_ - sourceCotoId),
          Cmd.none
        )

      case Msg.OrderChanged(sourceCotoId, Left(e)) =>
        (
          model.modify(_.reordering).using(_ - sourceCotoId),
          cotoami.error("Couldn't change the link order.", e)
        )

      case Msg.OutgoingLinksFetched(cotoId, Right(links)) =>
        (
          model.modify(_.links).using(_.putAll(links)),
          Cmd.none
        )

      case Msg.OutgoingLinksFetched(cotoId, Left(e)) =>
        (model, cotoami.error("Couldn't fetch outgoing links.", e))
    }

  def fetchNodeDetails(id: Id[Node]): Cmd.One[AppMsg] =
    NodeDetails.fetch(id).map(Msg.NodeDetailsFetched(_).into)

  def fetchCotonoma(id: Id[Cotonoma]): Cmd.One[AppMsg] =
    CotonomaBackend.fetch(id).map(Domain.Msg.CotonomaFetched(_).into)

  def fetchCotoDetails(id: Id[Coto]): Cmd.One[AppMsg] =
    CotoDetails.fetch(id).map(Msg.CotoDetailsFetched(_).into)

  def fetchLink(id: Id[Link]): Cmd.One[AppMsg] =
    LinkBackend.fetch(id).map(Domain.Msg.LinkFetched(_).into)

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd.One[AppMsg] =
    CotoGraph.fetchFromCoto(coto).map(Msg.CotoGraphFetched(_).into)

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd.One[AppMsg] =
    CotoGraph.fetchFromCotonoma(cotonoma).map(Msg.CotoGraphFetched(_).into)

  def deleteCoto(id: Id[Coto]): Cmd.One[AppMsg] =
    CotoBackend.delete(id).map(Msg.CotoDeleted(id, _).into)

  def changeOrder(link: Link, newOrder: Int): Cmd.One[AppMsg] =
    LinkBackend.changeOrder(link.id, newOrder)
      .map(Msg.OrderChanged(link.sourceCotoId, _).into)

  def fetchOutgoingLinks(cotoId: Id[Coto]): Cmd.One[AppMsg] =
    LinkBackend.fetchOutgoingLinks(cotoId)
      .map(Msg.OutgoingLinksFetched(cotoId, _).into)
}
