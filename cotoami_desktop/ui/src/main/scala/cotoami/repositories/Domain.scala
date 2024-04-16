package cotoami.repositories

import com.softwaremill.quicklens._

import fui.FunctionalUI._
import cotoami.{log_info, DomainMsg}
import cotoami.backend._

case class Domain(
    nodes: Nodes = Nodes(),
    cotonomas: Cotonomas = Cotonomas(),
    cotos: Cotos = Cotos(),
    links: Links = Links(),
    graphLoading: Boolean = false
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

  def currentCotonoma: Option[Cotonoma] =
    this.cotonomas.selectedId.orElse(
      this.nodes.current.map(_.rootCotonomaId)
    ).flatMap(this.cotonomas.get)

  def setCotonomaDetails(details: CotonomaDetailsJson): Domain = {
    val cotonoma = Cotonoma(details.cotonoma)
    this
      .modify(_.nodes).using(nodes =>
        if (!nodes.isSelecting(cotonoma.nodeId))
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

  def appendTimeline(cotos: PaginatedCotosJson): Domain =
    this
      .modify(_.cotos).using(_.appendTimeline(cotos))
      .modify(_.cotonomas).using(_.importFrom(cotos.related_data))

  def importCotoGraph(graph: CotoGraphJson): Domain =
    this
      .modify(_.graphLoading).setTo(false)
      .modify(_.cotos).using(_.importFrom(graph))
      .modify(_.cotonomas).using(_.importFrom(graph.cotos_related_data))
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
        (model.setCotonomaDetails(details), Seq.empty)

      case CotonomaDetailsFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch cotonoma details.")))

      case TimelineFetched(Right(cotos)) =>
        (
          model.appendTimeline(cotos),
          Seq(
            log_info("Timeline fetched.", Some(PaginatedCotosJson.debug(cotos)))
          )
        )

      case TimelineFetched(Left(e)) =>
        (
          model.modify(_.cotos.timelineLoading).setTo(false),
          Seq(ErrorJson.log(e, "Couldn't fetch timeline cotos."))
        )

      case CotoGraphFetched(Right(graph)) =>
        (
          model.importCotoGraph(graph),
          Seq(log_info("Coto graph fetched.", Some(CotoGraphJson.debug(graph))))
        )

      case CotoGraphFetched(Left(e)) =>
        (model, Seq(ErrorJson.log(e, "Couldn't fetch a coto graph.")))
    }

  def fetchCotoGraph(from: Id[Coto]): Cmd[cotoami.Msg] =
    Commands.send(Commands.CotoGraph(from)).map(
      Domain.CotoGraphFetched andThen DomainMsg
    )
}
