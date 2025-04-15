package cotoami.repository

import scala.util.chaining._
import scala.collection.immutable.HashSet
import scala.scalajs.js

import com.softwaremill.quicklens._

import marubinotto.fui._
import cotoami.{Into, Msg => AppMsg}
import cotoami.models._
import cotoami.backend._

case class Root(
    lastChangeNumber: Double = 0,

    // Entities
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    itos: Itos = Itos(),

    // Processing state
    graphLoading: HashSet[Id[Coto]] = HashSet.empty,
    graphLoaded: HashSet[Id[Coto]] = HashSet.empty,
    deleting: HashSet[Id[Coto]] = HashSet.empty,
    pinning: HashSet[Id[Coto]] = HashSet.empty,
    reordering: HashSet[Id[Coto]] = HashSet.empty
) {
  /////////////////////////////////////////////////////////////////////////////
  // Focus
  /////////////////////////////////////////////////////////////////////////////

  def focusNode(nodeId: Option[Id[Node]]): Root =
    clearProcessingState.copy(
      nodes = nodes.onNodeChange.focus(nodeId),
      cotonomas = Cotonomas(),
      cotos = cotos.onCotonomaChange(),
      itos = Itos()
    )

  def focusCotonoma(nodeId: Option[Id[Node]], cotonomaId: Id[Cotonoma]): Root =
    clearProcessingState.copy(
      nodes = nodes.focus(nodeId),
      cotonomas = cotonomas.focus(Some(cotonomaId)),
      cotos = cotos.onCotonomaChange(),
      itos = Itos()
    )

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

  def inFocus(coto: Coto): Boolean =
    (nodes.focusedId, cotonomas.focusedId) match {
      case (None, None)         => true
      case (Some(nodeId), None) => coto.nodeId == nodeId
      case (_, Some(cotonomaId)) =>
        coto.postedInIds.contains(cotonomaId) || (
          // if the coto is the current cotonoma itself
          cotonomas.getByCotoId(coto.id).map(_.id) == Some(cotonomaId)
        )
    }

  private def clearProcessingState: Root =
    copy(
      graphLoading = HashSet.empty,
      graphLoaded = HashSet.empty,
      deleting = HashSet.empty,
      pinning = HashSet.empty,
      reordering = HashSet.empty
    )

  /////////////////////////////////////////////////////////////////////////////
  // Cotos
  ///////////////////////////////////////////////////////////////////////////

  def isNodeRoot(cotoId: Id[Coto]): Boolean =
    cotonomas.getByCotoId(cotoId).map(nodes.isNodeRoot(_)).getOrElse(false)

  def deleteCoto(id: Id[Coto]): Root =
    // Delete the reposts first if they exist
    cotos.repostsOf(id).foldLeft(this)(_ deleteCoto _.id)
      // then, delete the coto (which could be a cotonoma)
      // and the itos to/from the coto.
      .modify(_.cotos).using(_.delete(id))
      .modify(_.cotonomas).using(cotonomas =>
        cotos.get(id)
          .map(cotonomas.deleteByCoto)
          .getOrElse(cotonomas.deleteByCotoId(id))
      )
      .modify(_.itos).using(_.onCotoDelete(id))

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

  def currentCotonomaCoto: Option[Coto] = currentCotonomaPair.map(_._2)

  def isCurrentCotonoma(cotoId: Id[Coto]): Boolean =
    currentCotonomaCoto.map(_.id == cotoId).getOrElse(false)

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

  /////////////////////////////////////////////////////////////////////////////
  // Itos
  /////////////////////////////////////////////////////////////////////////////

  lazy val pins: Siblings =
    currentCotonoma.map(cotonoma => childrenOf(cotonoma.cotoId))
      .getOrElse(Siblings.empty)

  def childrenOf(cotoId: Id[Coto]): Siblings =
    itos.from(cotoId).toSeq
      .map(ito => cotos.get(ito.targetCotoId).map(child => (ito, child)))
      .flatten
      .pipe(Siblings(_))

  def parentsOf(
      cotoId: Id[Coto],
      excludeCurrentCotonoma: Boolean = true
  ): Seq[(Coto, Ito)] =
    itos.to(cotoId)
      .map(ito =>
        cotos.get(ito.sourceCotoId).flatMap(parent =>
          if (
            excludeCurrentCotonoma &&
            currentCotonoma.map(_.cotoId == parent.id)
              .getOrElse(false)
          )
            None
          else
            Some((parent, ito))
        )
      )
      .flatten

  def isPin(ito: Ito): Boolean =
    currentCotonoma.map(ito.sourceCotoId == _.cotoId).getOrElse(false)

  def pinned(cotoId: Id[Coto]): Boolean =
    currentCotonoma.map(cotonoma =>
      itos.connected(cotonoma.cotoId, cotoId)
    ).getOrElse(false)

  def pin(cotoId: Id[Coto]): (Root, Cmd.One[AppMsg]) =
    currentCotonoma.map(cotonoma =>
      (
        this.modify(_.pinning).using(_ + cotoId),
        ItoBackend.create(
          cotonoma.cotoId,
          cotoId,
          None,
          None,
          None
        )
          .map(Root.Msg.Pinned(cotoId, _).into)
      )
    ).getOrElse((this, Cmd.none))

  def fetchGraph: Cmd.One[AppMsg] =
    currentCotonomaId
      .map(Root.fetchGraphFromCotonoma)
      .getOrElse(Cmd.none)

  def lazyFetchGraphFrom(cotoId: Id[Coto]): Cmd.One[AppMsg] =
    if (
      !alreadyLoadedGraphFrom(cotoId) &&
      (
        cotos.isCotonoma(cotoId).getOrElse(false) ||
          anyTargetMissingItosFrom(cotoId)
      )
    )
      Root.fetchGraphFromCoto(cotoId)
    else
      Cmd.none

  /////////////////////////////////////////////////////////////////////////////
  // Graph
  /////////////////////////////////////////////////////////////////////////////

  def alreadyLoadedGraphFrom(cotoId: Id[Coto]): Boolean =
    graphLoaded.contains(cotoId)

  def anyTargetMissingItosFrom(id: Id[Coto]): Boolean =
    itos.from(id).find(ito => !cotos.contains(ito.targetCotoId)).isDefined

  /////////////////////////////////////////////////////////////////////////////
  // Geolocation
  /////////////////////////////////////////////////////////////////////////////

  /** Returns the geolocation or bounds calculated from the cotos in the focus.
    *
    * NOTE: if the focus is on a cotonoma, the target cotonoma must have been
    * loaded, otherwise the cotonoma geolocation will not be used.
    */
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

  lazy val cotoMarkers: Seq[CotoMarker] = {
    var markers: Map[Geolocation, CotoMarker] = Map.empty
    cotos.geolocated.foreach { case (coto, location) =>
      nodes.get(coto.nodeId).foreach(node =>
        markers = markers.updatedWith(location) {
          case Some(marker) =>
            Some(marker.addCoto(coto, node.iconUrl, inFocus(coto)))
          case None =>
            Some(
              CotoMarker(
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

  lazy val canPostCoto: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.isWritable(cotonoma.nodeId)
      case None           => false
    }

  lazy val canPostCotonoma: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.canPostCotonoma(cotonoma.nodeId)
      case None           => false
    }

  lazy val canEditItos: Boolean =
    currentCotonoma match {
      case Some(cotonoma) => nodes.canEditItosIn(cotonoma.nodeId)
      case None           => false
    }

  def canRepost(cotoId: Id[Coto]): Boolean =
    // You can't repost the current node root.
    !Seq(
      currentNodeRoot.map(_._2.id)
    ).flatten.contains(cotoId)

  def canPin(cotoId: Id[Coto]): Boolean =
    canEditItos && !pinned(cotoId) &&
      // You can't pin the current cotonoma (obviously) and the current node root.
      !Seq(
        currentCotonomaPair.map(_._2.id),
        currentNodeRoot.map(_._2.id)
      ).flatten.contains(cotoId)

  def canDeleteEmpty(cotonoma: Cotonoma): Boolean =
    cotos.get(cotonoma.cotoId).map(nodes.canDelete).getOrElse(false)

  /////////////////////////////////////////////////////////////////////////////
  // Import
  /////////////////////////////////////////////////////////////////////////////

  def setCotonomaDetails(details: CotonomaDetails): Root =
    this
      .modify(_.nodes).using(nodes =>
        if (nodes.focusedId.map(_ != details.cotonoma.nodeId).getOrElse(false))
          nodes.focus(Some(details.cotonoma.nodeId))
        else
          nodes
      )
      .modify(_.cotonomas).using(_.setCotonomaDetails(details))
      .modify(_.cotos).using(_.put(details.coto))

  def importFrom(details: NodeDetails): Root =
    this
      .modify(_.nodes).using(_.put(details.node))
      .pipe { model =>
        details.root
          .map { case (cotonoma, coto) =>
            model
              .modify(_.cotonomas).using(_.put(cotonoma))
              .modify(_.cotos).using(_.put(coto))
          }
          .getOrElse(model)
      }

  def importFrom(details: CotoDetails): Root =
    this
      .modify(_.cotos).using(_.put(details.coto))
      .modify(_.cotonomas).using(_.importFrom(details.relatedData))
      .modify(_.itos).using(_.putAll(details.outgoingItos))

  def importFrom(cotonomaPair: (Cotonoma, Coto)): Root =
    this
      .modify(_.cotonomas).using(_.put(cotonomaPair._1))
      .modify(_.cotos).using(_.put(cotonomaPair._2))

  def importFrom(cotos: PaginatedCotos): Root =
    this
      .modify(_.cotos).using(_.importFrom(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.relatedData))
      .modify(_.itos).using(_.putAll(cotos.outgoingItos))

  def importFrom(cotos: GeolocatedCotos): Root =
    this
      .modify(_.cotos).using(_.importFrom(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.relatedData))

  def importFrom(graph: CotoGraph): Root =
    this
      .modify(_.graphLoaded).using(_ + graph.rootCotoId)
      .modify(_.cotos).using(_.importFrom(graph))
      .modify(_.cotonomas).using(_.importFrom(graph))
      .modify(_.itos).using(_.putAll(graph.itos))
}

object Root {

  /** Create a repository root with an InitialDataset.
    *
    * @param dataset
    *   The initial dataset of this repository.
    * @param localId
    *   The local node ID of the database this app has originally opened. This
    *   ID can be different from `dataset.localNodeId` when the operated node is
    *   switched to a remote node.
    */
  def apply(dataset: InitialDataset, localId: Id[Node]): Root =
    Root(
      lastChangeNumber = dataset.lastChangeNumber,
      nodes = Nodes(dataset, localId)
    )

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.RepositoryMsg(this)
  }

  object Msg {
    case class Reconnect(serverId: Id[Node], password: String) extends Msg
    case class Reconnected(result: Either[ErrorJson, ServerNode]) extends Msg
    case class NodeDetailsFetched(result: Either[ErrorJson, NodeDetails])
        extends Msg
    case class CotonomaFetched(result: Either[ErrorJson, (Cotonoma, Coto)])
        extends Msg
    case class CotoDetailsFetched(result: Either[ErrorJson, CotoDetails])
        extends Msg
    case class ItoFetched(result: Either[ErrorJson, Ito]) extends Msg
    case class FetchGraphFromCoto(cotoId: Id[Coto]) extends Msg
    case class CotoGraphFetched(result: Either[ErrorJson, CotoGraph])
        extends Msg
    case class DeleteCoto(id: Id[Coto]) extends Msg
    case class DeleteCotonoma(cotonoma: Cotonoma) extends Msg
    case class CotoDeleted(id: Id[Coto], result: Either[ErrorJson, Id[Coto]])
        extends Msg
    case class Pin(cotoId: Id[Coto]) extends Msg
    case class Pinned(cotoId: Id[Coto], result: Either[ErrorJson, Ito])
        extends Msg
    case class ChangeOrder(ito: Ito, newOrder: Int) extends Msg
    case class OrderChanged(
        sourceCotoId: Id[Coto],
        result: Either[ErrorJson, Ito]
    ) extends Msg
    case class OutgoingItosFetched(
        cotoId: Id[Coto],
        result: Either[ErrorJson, js.Array[Ito]]
    ) extends Msg
  }

  def update(msg: Msg, model: Root): (Root, Cmd[AppMsg]) =
    msg match {
      case Msg.Reconnect(serverId, password) =>
        (
          model,
          ServerNodeBackend.edit(serverId, None, Some(password), None)
            .map(Msg.Reconnected(_).into)
        )

      case Msg.Reconnected(Right(server)) =>
        (model.modify(_.nodes.servers).using(_.updateSpec(server)), Cmd.none)

      case Msg.Reconnected(Left(e)) =>
        (model, cotoami.error("Couldn't reconnect to a server.", e))

      case Msg.NodeDetailsFetched(Right(details)) =>
        (model.importFrom(details), Cmd.none)

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

      case Msg.ItoFetched(Right(ito)) =>
        (model.modify(_.itos).using(_.put(ito)), Cmd.none)

      case Msg.ItoFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch an ito.", e))

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

      case Msg.DeleteCotonoma(cotonoma) =>
        (
          model.modify(_.deleting).using(_ + cotonoma.cotoId),
          Cmd.Batch(
            deleteCoto(cotonoma.cotoId),
            Browser.send(AppMsg.UnfocusCotonoma)
          )
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

      case Msg.Pin(cotoId) => model.pin(cotoId)

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

      case Msg.ChangeOrder(ito, newOrder) =>
        (
          model.modify(_.reordering).using(_ + ito.sourceCotoId),
          changeOrder(ito, newOrder)
        )

      case Msg.OrderChanged(sourceCotoId, Right(_)) =>
        (
          model.modify(_.reordering).using(_ - sourceCotoId),
          Cmd.none
        )

      case Msg.OrderChanged(sourceCotoId, Left(e)) =>
        (
          model.modify(_.reordering).using(_ - sourceCotoId),
          cotoami.error("Couldn't change the ito order.", e)
        )

      case Msg.OutgoingItosFetched(cotoId, Right(itos)) =>
        (
          model.modify(_.itos).using(_.replaceOutgoingItos(cotoId, itos)),
          Cmd.none
        )

      case Msg.OutgoingItosFetched(cotoId, Left(e)) =>
        (model, cotoami.error("Couldn't fetch outgoing itos.", e))
    }

  def fetchNodeDetails(id: Id[Node]): Cmd.One[AppMsg] =
    NodeDetails.fetch(id).map(Msg.NodeDetailsFetched(_).into)

  def fetchCotonoma(id: Id[Cotonoma]): Cmd.One[AppMsg] =
    CotonomaBackend.fetch(id).map(Root.Msg.CotonomaFetched(_).into)

  def fetchCotoDetails(id: Id[Coto]): Cmd.One[AppMsg] =
    CotoDetails.fetch(id).map(Msg.CotoDetailsFetched(_).into)

  def fetchIto(id: Id[Ito]): Cmd.One[AppMsg] =
    ItoBackend.fetch(id).map(Root.Msg.ItoFetched(_).into)

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd.One[AppMsg] =
    CotoGraph.fetchFromCoto(coto).map(Msg.CotoGraphFetched(_).into)

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd.One[AppMsg] =
    CotoGraph.fetchFromCotonoma(cotonoma).map(Msg.CotoGraphFetched(_).into)

  def deleteCoto(id: Id[Coto]): Cmd.One[AppMsg] =
    CotoBackend.delete(id).map(Msg.CotoDeleted(id, _).into)

  def changeOrder(ito: Ito, newOrder: Int): Cmd.One[AppMsg] =
    ItoBackend.changeOrder(ito.id, newOrder)
      .map(Msg.OrderChanged(ito.sourceCotoId, _).into)

  def fetchOutgoingItos(cotoId: Id[Coto]): Cmd.One[AppMsg] =
    ItoBackend.fetchOutgoingItos(cotoId)
      .map(Msg.OutgoingItosFetched(cotoId, _).into)
}
