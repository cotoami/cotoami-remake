package cotoami

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL
import org.scalajs.dom.HTMLElement

import slinky.core.facade.{Fragment, ReactElement}
import slinky.hot
import slinky.web.html._

import com.softwaremill.quicklens._

import cats.effect.IO
import cats.syntax.all._

import fui._
import cotoami.tauri
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

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
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

      case Msg.BackendChange(log) => {
        val (domain, cmds) = model.domain.importChangelog(log)
        (
          model.copy(domain = domain)
            .debug(
              "BackendChange received.",
              Some(js.JSON.stringify(log))
            ),
          cmds
        )
      }

      case Msg.BackendEvent(event) =>
        (
          model.handleLocalNodeEvent(event)
            .debug(
              "BackendEvent received.",
              Some(js.JSON.stringify(event))
            ),
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
        (model, Seq(Browser.send(Msg.SetDatabaseInfo(info))))

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
        model.uiState
          .map(_.openOrClosePane(name, open) match {
            case state => (model.copy(uiState = Some(state)), Seq(state.save))
          })
          .getOrElse((model, Seq.empty))

      case Msg.ResizePane(name, newSize) =>
        model.uiState
          .map(_.resizePane(name, newSize) match {
            case state => (model.copy(uiState = Some(state)), Seq(state.save))
          })
          .getOrElse((model, Seq.empty))

      case Msg.SwitchPinnedView(cotonoma, inColumns) =>
        model.uiState
          .map(_.setPinnedInColumns(cotonoma, inColumns) match {
            case state => (model.copy(uiState = Some(state)), Seq(state.save))
          })
          .getOrElse((model, Seq.empty))

      case Msg.ScrollToPinnedCoto(pin) =>
        (
          model,
          Seq(
            Cmd(
              IO {
                dom.document.getElementById(
                  PaneStock.elementIdOfPinnedCoto(pin)
                ) match {
                  case element: HTMLElement =>
                    element.scrollIntoView(true)
                  case _ => ()
                }
                None
              }
            )
          )
        )

      case Msg.SelectNode(id) =>
        (model, Seq(Browser.pushUrl(Route.node.url(id))))

      case Msg.DeselectNode => {
        val url = model.domain.cotonomas.selected match {
          case None           => Route.index.url(())
          case Some(cotonoma) => Route.cotonoma.url(cotonoma.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case Msg.SelectCotonoma(cotonoma) => {
        val url = model.domain.nodes.selected match {
          case None => Route.cotonoma.url(cotonoma.id)
          case Some(_) =>
            if (model.domain.isRoot(cotonoma))
              Route.node.url(cotonoma.nodeId)
            else
              Route.cotonomaInNode.url((cotonoma.nodeId, cotonoma.id))
        }
        (model, Seq(Browser.pushUrl(url)))
      }

      case Msg.DeselectCotonoma => {
        val url = model.domain.nodes.selected match {
          case None       => Route.index.url(())
          case Some(node) => Route.node.url(node.id)
        }
        (model, Seq(Browser.pushUrl(url)))
      }

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
        val (submodel, cmds) = Domain.update(submsg, model.domain)
        (model.copy(domain = submodel), cmds)
      }

      case Msg.ModalMsg(submsg) => Modal.update(submsg, model)

      case Msg.NavCotonomasMsg(submsg) => {
        val (submodel, cmds) = NavCotonomas.update(submsg, model.navCotonomas)
        (model.copy(navCotonomas = submodel), cmds)
      }

      case Msg.FlowInputMsg(submsg) => {
        val (flowInput, waitingPosts, log, subcmds) = FormCoto.update(
          submsg,
          model.domain.currentCotonoma,
          model.flowInput,
          model.waitingPosts,
          model.log
        )
        (
          model.copy(
            flowInput = flowInput,
            waitingPosts = waitingPosts,
            log = log
          ),
          subcmds.map(_.map(Msg.FlowInputMsg))
        )
      }

      case Msg.SectionTimelineMsg(submsg) =>
        SectionTimeline.update(submsg, model)

      case Msg.SectionTraversalsMsg(submsg) => {
        val (submodel, cmds) = SectionTraversals.update(
          submsg,
          model.traversals,
          model.domain
        )
        (
          model.copy(traversals = submodel),
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
    }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) => {
        val (domain, cmds) = model.domain.selectNode(None)
        (model.copy(domain = domain), cmds)
      }

      case Route.node(id) =>
        if (model.domain.nodes.contains(id)) {
          val (domain, cmds) = model.domain.selectNode(Some(id))
          (model.copy(domain = domain), cmds)
        } else {
          (
            model.warn(s"Node [${id}] not found.", None),
            Seq(Browser.pushUrl(Route.index.url(())))
          )
        }

      case Route.cotonoma(id) => {
        val (domain, cmds) = model.domain.selectCotonoma(None, id)
        (model.copy(domain = domain), cmds)
      }

      case Route.cotonomaInNode((nodeId, cotonomaId)) => {
        val (domain, cmds) =
          model.domain.selectCotonoma(Some(nodeId), cotonomaId)
        (model.copy(domain = domain), cmds)
      }

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
    implicit val context: Context = model
    Fragment(
      AppHeader(model, dispatch),
      div(id := "app-body", className := "body")(
        model.uiState
          .map(AppBody.contents(model, _, dispatch))
          .getOrElse(Seq()): _*
      ),
      AppFooter(model, dispatch),
      if (model.logViewToggle)
        Some(ViewLog(model.log, dispatch))
      else
        None,
      Modal(model, dispatch)
    )
  }
}
