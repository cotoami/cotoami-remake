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

import fui.FunctionalUI._
import cotoami.tauri
import cotoami.components.{material_symbol, node_img, paneToggle, SplitPane}
import cotoami.backend.{Commands, LogEvent, SystemInfo}

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update, subscriptions)
    )
  }

  def init(url: URL): (Model, Seq[Cmd[Msg]]) =
    (
      Model(),
      Seq(
        Model.UiState.restore(UiStateRestored),
        cotoami.backend.SystemInfo.fetch().map(SystemInfoFetched(_))
      )
    )

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
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
            .modify(_.welcomeModal.baseFolder).setTo(systemInfo.app_data_dir)
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

      case DatabaseOpened(Right(node)) =>
        (
          model
            .modify(_.localNode).setTo(Some(node))
            .modify(_.operatingNodeId).setTo(Some(node.uuid))
            .modify(_.welcomeModal.processing).setTo(false)
            .modify(_.log).using(
              _.info(
                s"Database [${node.name}] opened.",
                Some(s"uuid: ${node.uuid}, name: ${node.name}")
              )
            ),
          Seq.empty
        )

      case DatabaseOpened(Left(e)) =>
        (
          model
            .modify(_.log).using(_.error(e.message, Option(e.details)))
            .modify(_.welcomeModal.processing).setTo(false)
            .modify(_.welcomeModal.systemError).setTo(Some(e.message)),
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

      case WelcomeModalMsg(subMsg) => {
        val (welcomeModal, cmds) =
          subparts.WelcomeModal.update(subMsg, model.welcomeModal);
        (model.copy(welcomeModal = welcomeModal), cmds)
      }

      case FetchLocalNode =>
        (model, Seq(node_command(Commands.LocalNode).map(LocalNodeFetched(_))))

      case LocalNodeFetched(Right(node)) =>
        (
          model
            .modify(_.localNode).setTo(Some(node))
            .modify(_.log).using(
              _.info(
                "Local node fetched.",
                Some(s"uuid: ${node.uuid}, name: ${node.name}")
              )
            ),
          Seq.empty
        )

      case LocalNodeFetched(Left(e)) =>
        (
          model.modify(_.log).using(
            _.error(
              "Couldn't fetch the local node.",
              Some(js.JSON.stringify(e))
            )
          ),
          Seq.empty
        )
    }

  def subscriptions(model: Model): Sub[Msg] =
    // Specify the type of the event payload (`LogEvent`) here,
    // otherwise a runtime error will occur for some reason
    tauri.listen[LogEvent]("log", None).map(BackendLogEvent(_))

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    Fragment(
      header(
        button(
          className := "app-info default",
          title := "View app info",
          onClick := ((e) => dispatch(cotoami.FetchLocalNode))
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
            section(className := "location")(
              a(className := "node-home", title := node.name)(node_img(node))
            )
          )
      ),
      div(id := "app-body", className := "body")(
        model.uiState
          .map(appBodyContent(model, _, dispatch))
          .getOrElse(Seq()): _*
      ),
      footer(
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
      ),
      if (model.logViewToggle)
        Some(subparts.LogView.view(model.log, dispatch))
      else
        None,
      modal(model, dispatch)
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

  def modal(model: Model, dispatch: Msg => Unit): Option[ReactElement] =
    if (model.localNode.isEmpty) {
      Some(
        subparts.WelcomeModal
          .view(
            model.welcomeModal,
            model.systemInfo.map(_.recent_databases.toSeq),
            dispatch
          )
      )
    } else {
      None
    }
}
