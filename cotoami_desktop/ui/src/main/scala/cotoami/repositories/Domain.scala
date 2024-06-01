package cotoami.repositories

import scala.collection.immutable.HashSet

import com.softwaremill.quicklens._

import fui._
import cotoami.log_info
import cotoami.backend._

case class Domain(
    lastChangeNumber: Double = 0,
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),
    graphLoading: HashSet[Id[Coto]] = HashSet.empty
) {
  def init(info: DatabaseInfo): Domain =
    this.copy(lastChangeNumber = info.lastChangeNumber, nodes = Nodes(info))

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

  def appendTimelinePage(cotos: PaginatedCotos): Domain =
    this
      .modify(_.cotos).using(_.appendTimelinePage(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.relatedData))
      .modify(_.links).using(_.addAll(cotos.outgoingLinks))

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

  def pinned(cotoId: Id[Coto]): Boolean =
    this.currentCotonoma.map(cotonoma =>
      this.links.linked(cotonoma.cotoId, cotoId)
    ).getOrElse(false)

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
            Domain.fetchTimeline(nodeId, None, None, 0),
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
            Domain.fetchCotonomaDetails(cotonomaId),
            Domain.fetchTimeline(None, Some(cotonomaId), None, 0),
            Domain.fetchGraphFromCotonoma(cotonomaId)
          )
        )
    }

  // Fetch the graph from the given coto if it has outgoing links that
  // have not yet been loaded (the target cotos of them should also be loaded).
  def lazyFetchGraphFromCoto(cotoId: Id[Coto]): Cmd[cotoami.Msg] =
    this.cotos.get(cotoId).map(coto => {
      if (this.childrenOf(cotoId).size < coto.outgoingLinks)
        Domain.fetchGraphFromCoto(cotoId)
      else
        Cmd.none
    }).getOrElse(Cmd.none)

  def importChangelog(
      log: ChangelogEntryJson
  ): (Domain, Seq[Cmd[cotoami.Msg]]) =
    if (log.serial_number == (this.lastChangeNumber + 1)) {
      this
        .applyChange(log.change)
        .modify(_._1.lastChangeNumber).setTo(log.serial_number)
    } else {
      (this, Seq(Browser.send(cotoami.ReloadDomain)))
    }

  private def applyChange(
      change: ChangeJson
  ): (Domain, Seq[Cmd[cotoami.Msg]]) = {
    // CreateCoto
    for (cotoJson <- change.CreateCoto.toOption) {
      val coto = Coto(cotoJson)
      return this
        .prependCotoToTimeline(coto)
        .prependCotonomaIdToRecent(coto.postedInId)
    }

    // CreateCotonoma
    for (cotonomaTuple <- change.CreateCotonoma.toOption) {
      val cotonoma = Cotonoma(cotonomaTuple._1)
      val coto = Coto(cotonomaTuple._2)
      return this
        .prependCotoToTimeline(coto)
        .prependCotonomaIdToRecent(coto.postedInId)
        .modify(_._1.cotonomas).using(_.prependToRecent(cotonoma))
    }

    (this, Seq.empty)
  }

  private def prependCotoToTimeline(coto: Coto): Domain =
    this.modify(_.cotos).using(cotos =>
      if (coto.postedInId == this.currentCotonomaId)
        cotos.prependToTimeline(coto)
      else
        cotos
    )

  private def prependCotonomaIdToRecent(
      id: Option[Id[Cotonoma]]
  ): (Domain, Seq[Cmd[cotoami.Msg]]) = {
    id.map(id => {
      val (cotonomas, cmds) = this.cotonomas.prependIdToRecent(id)
      (this.copy(cotonomas = cotonomas), cmds)
    }).getOrElse((this, Seq.empty))
  }
}

object Domain {

  sealed trait Msg {
    def asAppMsg: cotoami.Msg = cotoami.DomainMsg(this)
  }

  private def toAppMsg[T](tagger: T => Msg): T => cotoami.Msg =
    tagger andThen cotoami.DomainMsg

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
          model.appendTimelinePage(PaginatedCotos(cotos)),
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

  def fetchCotonomaDetails(id: Id[Cotonoma]): Cmd[cotoami.Msg] =
    Commands.send(Commands.CotonomaDetails(id))
      .map(toAppMsg(CotonomaDetailsFetched))

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
    ).map(toAppMsg(TimelineFetched))

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd[cotoami.Msg] =
    Commands.send(Commands.GraphFromCoto(coto))
      .map(toAppMsg(CotoGraphFetched))

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd[cotoami.Msg] =
    Commands.send(Commands.GraphFromCotonoma(cotonoma))
      .map(toAppMsg(CotoGraphFetched))
}
