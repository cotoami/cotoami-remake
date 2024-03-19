package cotoami

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.hot
import slinky.web.html._

import com.softwaremill.quicklens._
import trail._
import cats.effect.IO

import fui.FunctionalUI._
import cotoami.tauri
import cotoami.components.{material_symbol, node_img, paneToggle, SplitPane}
import cotoami.backend.{Commands, DatabaseInfo, LogEvent, SystemInfo}

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update, subscriptions, Some(UrlChanged(_)))
    )
  }

  object Route {
    val index = Root
  }

  object DatabaseFolder {
    val SessionStorageKey = "databaseFolder"

    def save(folder: String): Cmd[Msg] =
      Cmd(IO {
        dom.window.sessionStorage.setItem(SessionStorageKey, folder)
        None
      })

    def restore(): Cmd[Option[String]] =
      Cmd(IO {
        Some(Option(dom.window.sessionStorage.getItem(SessionStorageKey)))
      })
  }

  def init(url: URL): (Model, Seq[Cmd[Msg]]) =
    (
      Model(url = url),
      Seq(
        Model.UiState.restore(UiStateRestored),
        cotoami.backend.SystemInfo.fetch().map(SystemInfoFetched(_)),
        DatabaseFolder.restore().flatMap(
          _.map(openDatabase(_)).getOrElse(Cmd.none)
        )
      )
    )

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case UrlChanged(url) => applyUrlChange(url, model)

      case AddLogEntry(level, message, details) =>
        (
          model.copy(log = model.log.log(level, message, details)),
          Seq.empty
        )

      case BackendLogEvent(event) =>
        (
          model.copy(log = model.log.addEntry(LogEvent.toLogEntry(event))),
          Seq.empty
        )

      case ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Seq.empty)

      case SystemInfoFetched(Right(systemInfo)) =>
        (
          model
            .modify(_.systemInfo).setTo(Some(systemInfo))
            .modify(_.modalWelcome.baseFolder).setTo(systemInfo.app_data_dir)
            .modify(_.log).using(
              _.info("SystemInfo fetched.", Some(SystemInfo.debug(systemInfo)))
            ),
          Seq.empty
        )

      case SystemInfoFetched(Left(_)) => (model, Seq.empty)

      case UiStateRestored(uiState) =>
        (
          model.copy(uiState = Some(uiState.getOrElse(Model.UiState()))),
          Seq.empty
        )

      case DatabaseOpened(Right(info)) =>
        (
          model
            .modify(_.databaseFolder).setTo(Some(info.folder))
            .modify(_.lastChangeNumber).setTo(info.last_change_number)
            .modify(_.nodes).setTo(DatabaseInfo.nodes_as_map(info))
            .modify(_.localNodeId).setTo(Some(info.local_node_id))
            .modify(_.operatingNodeId).setTo(Some(info.local_node_id))
            .modify(_.parentNodeIds).setTo(info.parent_node_ids.toSeq)
            .modify(_.modalWelcome.processing).setTo(false)
            .modify(_.log).using(
              _.info("Database opened.", Some(DatabaseInfo.debug(info)))
            ),
          Seq(DatabaseFolder.save(info.folder))
        )

      case DatabaseOpened(Left(e)) =>
        (
          model
            .modify(_.log).using(_.error(e.message, Option(e.details)))
            .modify(_.modalWelcome.processing).setTo(false)
            .modify(_.modalWelcome.systemError).setTo(Some(e.message)),
          Seq.empty
        )

      case TogglePane(name) => {
        model.uiState match {
          case Some(s) => {
            val new_s = s.togglePane(name)
            (model.copy(uiState = Some(new_s)), Seq(new_s.save()))
          }
          case None => (model, Seq.empty)
        }
      }

      case ResizePane(name, newSize) => {
        model.uiState match {
          case Some(s) => {
            val new_s = s.resizePane(name, newSize)
            (model.copy(uiState = Some(new_s)), Seq(new_s.save()))
          }
          case None => (model, Seq.empty)
        }
      }

      case ModalWelcomeMsg(subMsg) => {
        val (modalWelcome, cmds) =
          subparts.ModalWelcome.update(subMsg, model.modalWelcome);
        (model.copy(modalWelcome = modalWelcome), cmds)
      }

      case FetchLocalNode =>
        (model, Seq(node_command(Commands.LocalNode).map(LocalNodeFetched(_))))

      case LocalNodeFetched(Right(node)) =>
        (
          model
            .modify(_.nodes).using(_ + (node.uuid -> node))
            .modify(_.localNodeId).setTo(Some(node.uuid))
            .modify(_.log).using(
              _.info(
                "Local node fetched.",
                Some(s"uuid: ${node.uuid}, name: ${node.name}")
              )
            ),
          Seq.empty
        )

      case LocalNodeFetched(Left(e)) =>
        (model.error(e, "Couldn't fetch the local node."), Seq.empty)

      case CotonomasFetched(Right(paginated)) =>
        (
          model
            .modify(_.cotonomas).using(
              _ ++ paginated.rows.map(c => (c.uuid, c)).toMap
            )
            .modify(_.recentCotonomaIds).setTo(
              paginated.rows.map(_.uuid).toSeq
            )
            .modify(_.log).using(
              _.info(s"${paginated.rows.size} recent cotonomas fetched.", None)
            ),
          Seq.empty
        )

      case CotonomasFetched(Left(e)) =>
        (model.error(e, "Couldn't fetch cotonomas."), Seq.empty)
    }

  def applyUrlChange(url: URL, model: Model): (Model, Seq[Cmd[Msg]]) =
    url.pathname + url.search + url.hash match {
      case Route.index(_) =>
        (
          model.clearSelection(),
          Seq(
            node_command(Commands.RecentCotonomas(None)).map(
              CotonomasFetched(_)
            )
          )
        )
    }

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    tauri.listen[LogEvent]("log", None).map(BackendLogEvent(_))

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    Fragment(
      appHeader(model, dispatch),
      div(id := "app-body", className := "body")(
        model.uiState
          .map(appBodyContent(model, _, dispatch))
          .getOrElse(Seq()): _*
      ),
      appFooter(model, dispatch),
      if (model.logViewToggle)
        Some(subparts.LogView.view(model.log, dispatch))
      else
        None,
      modal(model, dispatch)
    )

  def appHeader(model: Model, dispatch: Msg => Unit): ReactElement =
    header(
      button(
        className := "app-info default",
        title := "View app info"
      )(
        img(
          className := "app-icon",
          alt := "Cotoami",
          src := "/images/logo/logomark.svg"
        )
      ),
      model
        .currentNode()
        .map(node =>
          Fragment(
            section(className := "location")(
              a(className := "node-home", title := node.name)(node_img(node))
            ),
            model.selectedCotonoma().map(cotonoma =>
              Fragment(
                material_symbol("chevron_right"),
                h1(className := "current-cotonoma")(cotonoma.name)
              )
            )
          )
        )
    )

  def appBodyContent(
      model: Model,
      uiState: Model.UiState,
      dispatch: Msg => Unit
  ): Seq[ReactElement] = Seq(
    subparts.NavNodes.view(model, uiState, dispatch),
    SplitPane(
      vertical = true,
      initialPrimarySize = uiState.paneSizes.getOrElse(
        subparts.NavCotonomas.PaneName,
        subparts.NavCotonomas.DefaultWidth
      ),
      resizable = uiState.paneOpened(subparts.NavCotonomas.PaneName),
      className = Some("node-contents"),
      onPrimarySizeChanged = (
          (newSize) =>
            dispatch(ResizePane(subparts.NavCotonomas.PaneName, newSize))
      )
    )(
      subparts.NavCotonomas.view(model, uiState, dispatch),
      components.SplitPane.Secondary(className = None)(
        slinky.web.html.main()(
          section(className := "flow pane")(
            paneToggle("flow", dispatch),
            section(className := "timeline header-and-body")(
            )
          )
        )
      )
    )
  )

  def appFooter(model: Model, dispatch: Msg => Unit): ReactElement =
    footer(
      div(className := "browser-nav")(
        div(className := "path")(model.path())
      ),
      model.log
        .lastEntry()
        .map(entry =>
          div(className := s"log-peek ${entry.level.name}")(
            button(
              className := "open-log-view default",
              onClick := ((e) => dispatch(cotoami.ToggleLogView))
            )(
              material_symbol(entry.level.icon),
              entry.message
            )
          )
        )
    )

  def modal(model: Model, dispatch: Msg => Unit): Option[ReactElement] =
    if (model.localNodeId.isEmpty) {
      model.systemInfo.map(info =>
        subparts.ModalWelcome
          .view(
            model.modalWelcome,
            info.recent_databases.toSeq,
            dispatch
          )
      )
    } else {
      None
    }
}
