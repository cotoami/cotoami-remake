package cotoami.repositories

import scala.collection.immutable.HashSet
import com.softwaremill.quicklens._

import fui.FunctionalUI._
import cotoami.{log_info, DomainMsg}
import cotoami.backend._

case class Domain(
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),
    graphLoading: HashSet[Id[Coto]] = HashSet.empty
) {
  def initNodes(info: DatabaseInfo): Domain =
    this.copy(nodes = Nodes(info))

  def clearSelection(): Domain =
    this.copy(
      nodes = this.nodes.deselect(),
      cotonomas = Cotonomas(),
      cotos = Cotos(),
      links = Links()
    )

  def rootCotonomaId: Option[Id[Cotonoma]] =
    this.nodes.current.flatMap(node => Option(node.rootCotonomaId))

  def isRoot(id: Id[Cotonoma]): Boolean = Some(id) == this.rootCotonomaId

  def currentCotonomaId: Option[Id[Cotonoma]] =
    this.cotonomas.selectedId.orElse(
      this.nodes.current.map(_.rootCotonomaId)
    )

  // Note: Even if `currentCotonomaId` has `Some` value, this method will
  // return `None` if the cotonoma data of that ID has not been loaded.
  def currentCotonoma: Option[Cotonoma] =
    this.currentCotonomaId.flatMap(this.cotonomas.get)

  def setCotonomaDetails(details: CotonomaDetails): Domain = {
    this
      .modify(_.nodes).using(nodes =>
        if (!nodes.isSelecting(details.cotonoma.nodeId))
          nodes.deselect()
        else
          nodes
      )
      .modify(_.cotonomas).using(_.setCotonomaDetails(details))
  }

  def location: Option[(Node, Option[Cotonoma])] =
    this.nodes.current.map(currentNode =>
      // The location contains a cotonoma only when one is selected,
      // otherwise the root cotonoma of the current node will be implicitly
      // used as the current cotonoma.
      this.cotonomas.selected match {
        case Some(cotonoma) =>
          (
            this.nodes.get(cotonoma.nodeId).getOrElse(currentNode),
            Some(cotonoma)
          )
        case None => (currentNode, None)
      }
    )

  def appendTimeline(cotos: PaginatedCotos): Domain =
    this
      .modify(_.cotos).using(_.appendTimeline(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.relatedData))

  def importCotoGraph(graph: CotoGraph): Domain =
    this
      .modify(_.graphLoading).using(_ - graph.rootCotoId)
      .modify(_.cotos).using(_.importFrom(graph))
      .modify(_.cotonomas).using(_.importFrom(graph))
      .modify(_.links).using(_.addAll(graph.links))

  lazy val recentCotonomas: Seq[Cotonoma] = {
    val rootId = this.rootCotonomaId
    this.cotonomas.recent.filter(c => Some(c.id) != rootId)
  }

  lazy val superCotonomas: Seq[Cotonoma] = {
    val rootId = this.rootCotonomaId
    this.cotonomas.supers.filter(c => Some(c.id) != rootId)
  }

  lazy val timeline: Seq[Coto] =
    this.nodes.current match {
      case Some(node) =>
        this.cotos.timeline.filter(_.nameAsCotonoma != Some(node.name))
      case None => this.cotos.timeline
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

  def selectNode(nodeId: Option[Id[Node]]): (Domain, Seq[Cmd[cotoami.Msg]]) =
    this
      .clearSelection()
      .modify(_.nodes).using(nodes =>
        nodeId.map(nodes.select(_)).getOrElse(nodes)
      )
      .modify(_.cotonomas.recentLoading).setTo(true)
      .modify(_.cotos.timelineLoading).setTo(true) match {
      case domain =>
        (
          domain,
          Seq(
            Cotonomas.fetchRecent(nodeId, 0),
            Cotos.fetchTimeline(nodeId, None, 0),
            domain.currentCotonomaId
              .map(Domain.fetchGraphFromCotonoma)
              .getOrElse(Cmd.none)
          )
        )
    }

  def selectCotonoma(
      nodeId: Option[Id[Node]],
      cotonomaId: Id[Cotonoma]
  ): (Domain, Seq[Cmd[cotoami.Msg]]) =
    this
      .modify(_.nodes).using(nodes =>
        nodeId.map(nodes.select(_)).getOrElse(nodes.deselect())
      )
      .modify(_.cotonomas).using(_.select(cotonomaId))
      .modify(_.cotos).setTo(Cotos())
      .modify(_.links).setTo(Links())
      .modify(_.cotonomas.recentLoading).setTo(this.cotonomas.isEmpty)
      .modify(_.cotos.timelineLoading).setTo(true) match {
      case domain =>
        (
          domain,
          Seq(
            if (domain.cotonomas.isEmpty)
              Cotonomas.fetchRecent(nodeId, 0)
            else
              Cmd.none,
            Cotonomas.fetchDetails(cotonomaId),
            Cotos.fetchTimeline(None, Some(cotonomaId), 0),
            Domain.fetchGraphFromCotonoma(cotonomaId)
          )
        )
    }

  def lazyFetchGraphFromCoto(cotoId: Id[Coto]): Cmd[cotoami.Msg] =
    this.cotos.get(cotoId).map(coto => {
      // Fetch the graph from the coto if there are outgoing links that
      // have not yet been loaded (the target cotos of them should also be loaded).
      if (this.childrenOf(cotoId).size < coto.outgoingLinks)
        Domain.fetchGraphFromCoto(cotoId)
      else
        Cmd.none
    }).getOrElse(Cmd.none)
}

object Domain {

  sealed trait Msg

  case class CotonomasMsg(subMsg: Cotonomas.Msg) extends Msg
  case class CotosMsg(subMsg: Cotos.Msg) extends Msg

  case class CotonomaDetailsFetched(
      result: Either[ErrorJson, CotonomaDetailsJson]
  ) extends Msg
  case class TimelineFetched(result: Either[ErrorJson, PaginatedCotosJson])
      extends Msg

  case class FetchGraphFromCoto(cotoId: Id[Coto]) extends Msg
  case class CotoGraphFetched(result: Either[ErrorJson, CotoGraphJson])
      extends Msg

  def update(msg: Msg, model: Domain): (Domain, Seq[Cmd[cotoami.Msg]]) =
    msg match {
      case CotonomasMsg(subMsg) => {
        val (cotonomas, cmds) =
          Cotonomas.update(
            subMsg,
            model.cotonomas,
            model.nodes.selectedId
          )
        (model.copy(cotonomas = cotonomas), cmds)
      }

      case CotosMsg(subMsg) => {
        val (cotos, cmds) =
          Cotos.update(
            subMsg,
            model.cotos,
            model.nodes.selectedId,
            model.cotonomas.selectedId
          )
        (model.copy(cotos = cotos), cmds)
      }

      case CotonomaDetailsFetched(Right(details)) =>
        (
          model.setCotonomaDetails(CotonomaDetails(details)),
          Seq(
            log_info(
              "Cotonoma details fetched.",
              Some(CotonomaDetailsJson.debug(details))
            )
          )
        )

      case CotonomaDetailsFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch cotonoma details.")))

      case TimelineFetched(Right(cotos)) =>
        (
          model.appendTimeline(PaginatedCotos(cotos)),
          Seq(
            log_info("Timeline fetched.", Some(PaginatedCotosJson.debug(cotos)))
          )
        )

      case TimelineFetched(Left(e)) =>
        (
          model.modify(_.cotos.timelineLoading).setTo(false),
          Seq(ErrorJson.log(e, "Couldn't fetch timeline cotos."))
        )

      case FetchGraphFromCoto(cotoId) =>
        (
          model.modify(_.graphLoading).using(_ + cotoId),
          Seq(fetchGraphFromCoto(cotoId))
        )

      case CotoGraphFetched(Right(graph)) =>
        (
          model.importCotoGraph(CotoGraph(graph)),
          Seq(log_info("Coto graph fetched.", Some(CotoGraphJson.debug(graph))))
        )

      case CotoGraphFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch a coto graph.")))
    }

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd[cotoami.Msg] =
    Commands.send(Commands.GraphFromCoto(coto)).map(
      Domain.CotoGraphFetched andThen DomainMsg
    )

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd[cotoami.Msg] =
    Commands.send(Commands.GraphFromCotonoma(cotonoma)).map(
      Domain.CotoGraphFetched andThen DomainMsg
    )
}
