package cotoami

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core._
import slinky.core.facade.{ReactElement, Fragment}
import slinky.hot
import slinky.web.html._

import fui.FunctionalUI._

object Main {

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update)
    )
  }

  def init(url: URL): (Model, Seq[Cmd[Msg]]) =
    (
      Model(),
      Seq(
        Model.UiState.restore(UiStateRestored),
        cotoami.backend.SystemInfo.fetch(SystemInfoFetched)
      )
    )

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case AddLogEntry(level, message, details) =>
        (
          model.copy(log = model.log.addEntry(level, message, details)),
          Seq.empty
        )

      case ToggleLogView =>
        (model.copy(logViewToggle = !model.logViewToggle), Seq.empty)

      case SystemInfoFetched(Right(systemInfo)) =>
        (
          model.copy(
            systemInfo = Some(systemInfo),
            welcomeModal =
              model.welcomeModal.copy(baseFolder = systemInfo.app_data_dir),
            log = model.log
              .info("SystemInfo fetched.", Some(js.JSON.stringify(systemInfo)))
          ),
          Seq.empty
        )

      case SystemInfoFetched(Left(_)) => (model, Seq.empty)

      case UiStateRestored(uiState) =>
        (
          model.copy(uiState = Some(uiState.getOrElse(Model.UiState()))),
          Seq.empty
        )

      case DatabaseCreated(Right(node)) =>
        (
          model.copy(localNode = Some(node), currentNode = Some(node)),
          Seq.empty
        )

      case DatabaseCreated(Left(e)) =>
        (
          model.copy(log = model.log.error(e.message, Option(e.details))),
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
    }

  def view(model: Model, dispatch: Msg => Unit): ReactElement =
    Fragment(
      header(
        button(className := "app-info icon", title := "View app info")(
          img(
            className := "app-icon",
            alt := "Cotoami",
            src := "/images/logo/logomark.svg"
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
                className := "open-log-view",
                onClick := ((e) => dispatch(cotoami.ToggleLogView))
              )(
                cotoami.icon(entry.level.icon),
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
      SplitPane.Secondary(className = None)(
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
    if (model.currentNode.isEmpty) {
      Some(subparts.WelcomeModal.view(model.welcomeModal, dispatch))
    } else {
      None
    }
}
