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

  def init(url: URL): (Model, Seq[Cmd[Msg]]) = (Model(), Seq.empty)

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case TogglePane(name) =>
        (model.copy(uiState = model.uiState.togglePane(name)), Seq.empty)

      case Input(input) =>
        (model.copy(input = input), Seq.empty)

      case Send =>
        (
          model.copy(messages = model.messages :+ model.input, input = ""),
          Seq.empty
        )
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
        subparts.NavNodes.view(model, dispatch),
        SplitPane(
          vertical = true,
          initialPrimarySize = 230,
          resizable = model.uiState.paneOpened("nav-cotonomas"),
          className = Some("node-contents"),
          onPrimarySizeChanged = (newSize) => {
            println(s"node-contents changed: $newSize")
          }
        )(
          subparts.NavCotonomas.view(model, dispatch),
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
      ),
      footer()
    )
}
