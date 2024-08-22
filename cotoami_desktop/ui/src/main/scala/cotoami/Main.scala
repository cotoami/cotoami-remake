package cotoami

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core.facade.{Fragment, ReactElement}
import slinky.hot

import com.softwaremill.quicklens._

import cats.effect.IO
import cats.syntax.all._

import fui._
import cotoami.libs.tauri
import cotoami.backend._
import cotoami.repositories._
import cotoami.models._
import cotoami.subparts._

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update, subscriptions, Some(Msg.UrlChanged))
    )
  }

  object DatabaseFolder {
    val SessionStorageKey = "DatabaseFolder"

    def save(folder: String): Cmd[Msg] = Cmd(IO {
      dom.window.sessionStorage.setItem(SessionStorageKey, folder)
      None
    })

    def restore: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.sessionStorage.getItem(SessionStorageKey)))
    })
  }

  def init(url: URL): (Model, Seq[Cmd[Msg]]) = {
    val (flowInput, flowInputCmd) = FormCoto.init("flowInput", true)
    (
      Model(url = url, flowInput = flowInput),
      Seq(
        UiState.restore(Msg.UiStateRestored),
        cotoami.backend.SystemInfoJson.fetch().map(Msg.SystemInfoFetched),
        DatabaseFolder.restore.flatMap(
          _.map(DatabaseInfo.openDatabase(_).map(Msg.DatabaseOpened))
            .getOrElse(Modal.open(Modal.Welcome()))
        ),
        flowInputCmd.map(Msg.FlowInputMsg)
      )
    )
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) = {
    implicit val context: Context = model
    msg match {
      case Msg.UrlChanged(url) => applyUrlChange(url, model.changeUrl(url))

      case Msg.AddLogEntry(level, message, details) =>
        (
          model.modify(_.log).using(_.log(level, message, details)),
          Seq.empty
        )

      case Msg.LogEvent(event) =>
        (
          model.modify(_.log).using(
            _.addEntry(LogEventJson.toLogEntry(event))
          ),
          Seq.empty
        )

      case Msg.BackendChange(log) =>
        model
          .debug("BackendChange received.", Some(js.JSON.stringify(log)))
          .importChangelog(log)

      case Msg.BackendEvent(event) =>
        (
          model
            .debug("BackendEvent received.", Some(js.JSON.stringify(event)))
            .handleLocalNodeEvent(event),
          Seq.empty
        )

      case Msg.ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Seq.empty)

      case Msg.SystemInfoFetched(Right(systemInfo)) =>
        (
          model
            .modify(_.systemInfo).setTo(Some(systemInfo))
            .modify(_.time).using(
              _.setZoneOffsetInSeconds(systemInfo.time_zone_offset_in_sec)
            )
            .modify(
              _.modalStack.modals.each.when[Modal.Welcome].model.baseFolder
            ).setTo(Nullable.toOption(systemInfo.app_data_dir).getOrElse(""))
            .info(
              "SystemInfo fetched.",
              Some(SystemInfoJson.debug(systemInfo))
            ),
          Seq.empty
        )

      case Msg.SystemInfoFetched(Left(_)) => (model, Seq.empty)

      case Msg.UiStateRestored(uiState) =>
        (
          model
            .modify(_.uiState).setTo(Some(uiState.getOrElse(UiState())))
            .info("UiState restored.", Some(uiState.toString())),
          Seq.empty
        )

      case Msg.DatabaseOpened(Right(info)) =>
        update(Msg.SetDatabaseInfo(info), model)

      case Msg.DatabaseOpened(Left(e)) =>
        (model.error(e.default_message, Some(e)), Seq())

      case Msg.SetDatabaseInfo(info) => {
        model
          .modify(_.databaseFolder).setTo(Some(info.folder))
          .modify(_.domain).setTo(Domain(info.initialDataset, info.localNodeId))
          .info("Database opened.", Some(info.debug)) match {
          case model =>
            applyUrlChange(model.url, model).modify(_._2).using(
              Seq(
                DatabaseFolder.save(info.folder),
                connectToServers()
              ) ++ _
            )
        }
      }

      case Msg.ServerConnectionsInitialized(result) =>
        (
          result match {
            case Right(_) =>
              model.debug("Server connections initialized.", None)
            case Left(e) =>
              model.error("Failed to initialize server connections.", Some(e))
          },
          Seq.empty
        )

      case Msg.SetRemoteInitialDataset(dataset) =>
        (
          model
            .modify(_.domain).setTo(
              Domain(dataset, model.domain.nodes.localId.get)
            )
            .info("Remote dataset received.", Some(dataset.debug)),
          Seq(Browser.pushUrl(Route.index.url(())))
        )

      case Msg.OpenOrClosePane(name, open) =>
        model.updateUiState(_.openOrClosePane(name, open))

      case Msg.ResizePane(name, newSize) =>
        model.updateUiState(_.resizePane(name, newSize))

      case Msg.OpenGeomap =>
        model.updateUiState(_.openGeomap)

      case Msg.CloseMap =>
        model.updateUiState(_.closeMap)

      case Msg.FocusNode(id) =>
        (model, Seq(Browser.pushUrl(Route.node.url(id))))

      case Msg.UnfocusNode => {
        val url = model.domain.cotonomas.focused match {
          case None           => Route.index.url(())
          case Some(cotonoma) => Route.cotonoma.url(cotonoma.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case Msg.FocusCotonoma(cotonoma) => {
        val url = model.domain.nodes.focused match {
          case None => Route.cotonoma.url(cotonoma.id)
          case Some(_) =>
            if (model.domain.isRoot(cotonoma))
              Route.node.url(cotonoma.nodeId)
            else
              Route.cotonomaInNode.url((cotonoma.nodeId, cotonoma.id))
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case Msg.UnfocusCotonoma => {
        val url = model.domain.nodes.focused match {
          case None       => Route.index.url(())
          case Some(node) => Route.node.url(node.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case Msg.FocusCoto(id) =>
        (
          model.modify(_.domain.cotos).using(_.focus(id)),
          Seq(model.domain.lazyFetchGraphFromCoto(id))
        )

      case Msg.UnfocusCoto =>
        (
          model.modify(_.domain.cotos).using(_.unfocus),
          Seq.empty
        )

      case Msg.ReloadDomain => {
        (
          model.copy(domain = Domain()),
          Seq(
            model.databaseFolder.map(
              DatabaseInfo.openDatabase(_).map(Msg.DatabaseOpened)
            ).getOrElse(Cmd.none)
          )
        )
      }

      case Msg.DomainMsg(submsg) => {
        val (domain, cmds) = Domain.update(submsg, model.domain)
        (model.copy(domain = domain), cmds)
      }

      case Msg.ModalMsg(submsg) => Modal.update(submsg, model)

      case Msg.NavCotonomasMsg(submsg) => {
        val (navCotonomas, cmds) =
          NavCotonomas.update(submsg, model.navCotonomas)
        (model.copy(navCotonomas = navCotonomas), cmds)
      }

      case Msg.FlowInputMsg(submsg) => {
        val (flowInput, geomap, waitingPosts, log, subcmds) = FormCoto.update(
          submsg,
          model.flowInput,
          model.geomap,
          model.waitingPosts
        )(model)
        (
          model.copy(
            flowInput = flowInput,
            geomap = geomap,
            waitingPosts = waitingPosts,
            log = log
          ),
          subcmds.map(_.map(Msg.FlowInputMsg))
        )
      }

      case Msg.SectionTimelineMsg(submsg) => {
        val (timeline, domain, cmds) =
          SectionTimeline.update(submsg, model.timeline)
        (model.copy(timeline = timeline, domain = domain), cmds)
      }

      case Msg.SectionPinnedCotosMsg(submsg) =>
        SectionPinnedCotos.update(submsg, model)

      case Msg.SectionTraversalsMsg(submsg) => {
        val (traversals, cmds) = SectionTraversals.update(
          submsg,
          model.traversals,
          model.domain
        )
        (
          model.copy(traversals = traversals),
          (submsg, model.uiState) match {
            // Open the stock pane on OpenTraversal if it's closed.
            case (SectionTraversals.Msg.OpenTraversal(_), Some(uiState))
                if !uiState.paneOpened(PaneStock.PaneName) =>
              Browser.send(
                Msg.OpenOrClosePane(PaneStock.PaneName, true)
              ) +: cmds
            case _ => cmds
          }
        )
      }

      case Msg.SectionGeomapMsg(submsg) => {
        val (geomap, cmds) = SectionGeomap.update(submsg, model.geomap)
        (model.copy(geomap = geomap), cmds)
      }
    }
  }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) =>
        model.focusNode(None)

      case Route.node(id) =>
        if (model.domain.nodes.contains(id))
          model.focusNode(Some(id))
        else
          (
            model.warn(s"Node [${id}] not found.", None),
            Seq(Browser.pushUrl(Route.index.url(())))
          )

      case Route.cotonoma(id) =>
        model.focusCotonoma(None, id)

      case Route.cotonomaInNode((nodeId, cotonomaId)) =>
        model.focusCotonoma(Some(nodeId), cotonomaId)

      case _ =>
        (model, Seq(Browser.pushUrl(Route.index.url(()))))
    }

  private def connectToServers(): Cmd[Msg] =
    tauri.invokeCommand("connect_to_servers").map(
      Msg.ServerConnectionsInitialized
    )

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    (tauri.listen[LogEventJson]("log", None).map(Msg.LogEvent): Sub[Msg]) <+>
      this.listenToBackendChanges(model) <+>
      (tauri.listen[LocalNodeEventJson]("backend-event", None)
        .map(Msg.BackendEvent))

  private def listenToBackendChanges(model: Model): Sub[Msg] =
    if (model.modalStack.opened[Modal.ParentSync])
      Sub.Empty
    else
      tauri.listen[ChangelogEntryJson]("backend-change", None)
        .map(Msg.BackendChange)

  def view(model: Model, dispatch: Msg => Unit): ReactElement = {
    implicit val _context: Context = model
    implicit val _dispatch = dispatch
    Fragment(
      AppHeader(model),
      AppBody(model),
      AppFooter(model),
      if (model.logViewToggle)
        Some(ViewLog(model.log))
      else
        None,
      Modal(model)
    )
  }
}
