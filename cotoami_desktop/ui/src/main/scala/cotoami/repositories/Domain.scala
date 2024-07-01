package cotoami.repositories

import scala.collection.immutable.HashSet

import com.softwaremill.quicklens._

import fui._
import cotoami.{log_info, Msg => AppMsg}
import cotoami.backend._

case class Domain(
    lastChangeNumber: Double = 0,
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),
    graphLoading: HashSet[Id[Coto]] = HashSet.empty
) {
  def init(dataset: InitialDataset): Domain =
    this.copy(
      lastChangeNumber = dataset.lastChangeNumber,
      nodes = Nodes(dataset)
    )

  def clearSelection(): Domain =
    this.copy(
      nodes = this.nodes.select(None),
      cotonomas = Cotonomas(),
      cotos = Cotos(),
      links = Links()
    )

  def currentRootCotonomaId: Option[Id[Cotonoma]] =
    this.nodes.current.flatMap(_.rootCotonomaId)

  def isCurrentRoot(id: Id[Cotonoma]): Boolean =
    Some(id) == this.currentRootCotonomaId

  def currentCotonomaId: Option[Id[Cotonoma]] =
    this.cotonomas.selectedId.orElse(
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

  def isRoot(cotonoma: Cotonoma): Boolean =
    this.nodes.get(cotonoma.nodeId)
      .map(_.rootCotonomaId == Some(cotonoma.id))
      .getOrElse(false)

  def setCotonomaDetails(details: CotonomaDetails): Domain = {
    this
      .modify(_.nodes).using(nodes =>
        if (nodes.selectedId.map(_ != details.cotonoma.nodeId).getOrElse(false))
          nodes.select(Some(details.cotonoma.nodeId))
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
    val rootId = this.currentRootCotonomaId
    this.cotonomas.recent.filter(c => Some(c.id) != rootId)
  }

  lazy val superCotonomas: Seq[Cotonoma] = {
    val rootId = this.currentRootCotonomaId
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

  def selectNode(nodeId: Option[Id[Node]]): (Domain, Seq[Cmd[AppMsg]]) =
    this
      .clearSelection()
      .modify(_.nodes).using(_.select(nodeId))
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
  ): (Domain, Seq[Cmd[AppMsg]]) = {
    val shouldFetchCotonomas =
      // the selected node is changed
      nodeId != this.nodes.selectedId ||
        // or no recent cotonomas has been loaded yet (which means the page being reloaded)
        this.cotonomas.recentIds.isEmpty
    val (cotonomas, cmds) = this.cotonomas.selectAndFetch(cotonomaId)
    this
      .modify(_.nodes).using(_.select(nodeId))
      .modify(_.cotonomas).setTo(cotonomas)
      .modify(_.cotos).setTo(Cotos())
      .modify(_.links).setTo(Links())
      .modify(_.cotonomas.recentLoading).setTo(shouldFetchCotonomas)
      .modify(_.cotos.timelineLoading).setTo(true) match {
      case domain =>
        (
          domain,
          cmds ++ Seq(
            if (shouldFetchCotonomas)
              Cotonomas.fetchRecent(nodeId, 0)
            else
              Cmd.none,
            Domain.fetchTimeline(None, Some(cotonomaId), None, 0),
            Domain.fetchGraphFromCotonoma(cotonomaId)
          )
        )
    }
  }

  // Fetch the graph from the given coto if it has outgoing links that
  // have not yet been loaded (the target cotos of them should also be loaded).
  def lazyFetchGraphFromCoto(cotoId: Id[Coto]): Cmd[AppMsg] =
    this.cotos.get(cotoId).map(coto => {
      if (this.childrenOf(cotoId).size < coto.outgoingLinks)
        Domain.fetchGraphFromCoto(cotoId)
      else
        Cmd.none
    }).getOrElse(Cmd.none)

  def importChangelog(
      log: ChangelogEntryJson
  ): (Domain, Seq[Cmd[AppMsg]]) =
    if (log.serial_number == (this.lastChangeNumber + 1)) {
      this
        .applyChange(log.change)
        .modify(_._1.lastChangeNumber).setTo(log.serial_number)
    } else {
      (this, Seq(Browser.send(AppMsg.ReloadDomain)))
    }

  private def applyChange(
      change: ChangeJson
  ): (Domain, Seq[Cmd[AppMsg]]) = {
    // CreateCoto
    for (cotoJson <- change.CreateCoto.toOption) {
      return this
        .postCoto(cotoJson)

    }

    // CreateCotonoma
    for (cotonomaJson <- change.CreateCotonoma.toOption) {
      return this.postCotonoma(cotonomaJson)
    }

    // CreateLink
    for (linkJson <- change.CreateLink.toOption) {
      return (this.addLink(Link(linkJson)), Seq.empty)
    }

    // UpsertNode
    for (nodeJson <- change.UpsertNode.toOption) {
      return (this.addNode(Node(nodeJson)), Seq.empty)
    }

    // CreateNode
    for (createNodeJson <- change.CreateNode.toOption) {
      val domain = this.addNode(Node(createNodeJson.node))
      return Nullable.toOption(createNodeJson.root)
        .map(this.postCotonoma(_))
        .getOrElse((domain, Seq.empty))
    }

    (this, Seq.empty)
  }

  private def addNode(node: Node): Domain =
    this.modify(_.nodes).using(_.add(node))

  private def postCoto(cotoJson: CotoJson): (Domain, Seq[Cmd[AppMsg]]) = {
    val coto = Coto(cotoJson, true)
    this.modify(_.cotos).using(cotos =>
      if (this.inCurrentRoot || coto.postedInId == this.currentCotonomaId)
        cotos.post(coto)
      else
        cotos
    ).cotonomaUpdated(coto.postedInId)
  }

  private def postCotonoma(
      jsonPair: (CotonomaJson, CotoJson)
  ): (Domain, Seq[Cmd[AppMsg]]) = {
    val cotonoma = Cotonoma(jsonPair._1)
    val coto = Coto(jsonPair._2)
    this
      .modify(_.cotonomas).using(_.post(cotonoma, coto))
      .postCoto(jsonPair._2)
  }

  private def cotonomaUpdated(
      id: Option[Id[Cotonoma]]
  ): (Domain, Seq[Cmd[AppMsg]]) = {
    id.map(id => {
      val (cotonomas, cmds) = this.cotonomas.updated(id)
      (this.copy(cotonomas = cotonomas), cmds)
    }).getOrElse((this, Seq.empty))
  }

  private def addLink(link: Link): Domain =
    this.modify(_.links).using(_.add(link))
}

object Domain {

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.DomainMsg(this)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen AppMsg.DomainMsg

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
  }

  def update(msg: Msg, model: Domain): (Domain, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.CotonomasMsg(subMsg) => {
        val (cotonomas, cmds) =
          Cotonomas.update(
            subMsg,
            model.cotonomas,
            model.nodes.selectedId
          )
        (model.copy(cotonomas = cotonomas), cmds)
      }

      case Msg.CotosMsg(subMsg) => {
        val (cotos, cmds) =
          Cotos.update(
            subMsg,
            model.cotos,
            model.nodes.selectedId,
            model.cotonomas.selectedId
          )
        (model.copy(cotos = cotos), cmds)
      }

      case Msg.CotonomaDetailsFetched(Right(details)) =>
        (
          model.setCotonomaDetails(CotonomaDetails(details)),
          Seq(
            log_info(
              "Cotonoma details fetched.",
              Some(CotonomaDetailsJson.debug(details))
            )
          )
        )

      case Msg.CotonomaDetailsFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch cotonoma details.")))

      case Msg.TimelineFetched(Right(cotos)) =>
        (
          model.appendTimelinePage(PaginatedCotos(cotos)),
          Seq(
            log_info("Timeline fetched.", Some(PaginatedCotosJson.debug(cotos)))
          )
        )

      case Msg.TimelineFetched(Left(e)) =>
        (
          model.modify(_.cotos.timelineLoading).setTo(false),
          Seq(ErrorJson.log(e, "Couldn't fetch timeline cotos."))
        )

      case Msg.FetchGraphFromCoto(cotoId) =>
        (
          model.modify(_.graphLoading).using(_ + cotoId),
          Seq(fetchGraphFromCoto(cotoId))
        )

      case Msg.CotoGraphFetched(Right(graph)) =>
        (
          model.importCotoGraph(CotoGraph(graph)),
          Seq(log_info("Coto graph fetched.", Some(CotoGraphJson.debug(graph))))
        )

      case Msg.CotoGraphFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch a coto graph.")))
    }

  def fetchCotonomaDetails(id: Id[Cotonoma]): Cmd[AppMsg] =
    Commands.send(Commands.CotonomaDetails(id))
      .map(Msg.toApp(Msg.CotonomaDetailsFetched))

  def fetchTimeline(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      query: Option[String],
      pageIndex: Double
  ): Cmd[AppMsg] =
    query.map(query =>
      Commands.send(Commands.SearchCotos(query, nodeId, cotonomaId, pageIndex))
    ).getOrElse(
      Commands.send(Commands.RecentCotos(nodeId, cotonomaId, pageIndex))
    ).map(Msg.toApp(Msg.TimelineFetched))

  def fetchGraphFromCoto(coto: Id[Coto]): Cmd[AppMsg] =
    Commands.send(Commands.GraphFromCoto(coto))
      .map(Msg.toApp(Msg.CotoGraphFetched))

  def fetchGraphFromCotonoma(cotonoma: Id[Cotonoma]): Cmd[AppMsg] =
    Commands.send(Commands.GraphFromCotonoma(cotonoma))
      .map(Msg.toApp(Msg.CotoGraphFetched))
}
