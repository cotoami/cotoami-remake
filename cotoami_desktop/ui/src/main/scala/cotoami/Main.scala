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
        nav(className := "nodes pane", aria - "label" := "Nodes")(
          paneToggle(),
          button(
            className := "all-nodes icon selectable selected",
            data - "tooltip" := "All nodes",
            data - "placement" := "right"
          )(
            span(className := "material-symbols")("stacks")
          ),
          button(
            className := "add-node icon",
            data - "tooltip" := "Add node",
            data - "placement" := "right"
          )(
            span(className := "material-symbols")("add")
          ),
          ul(className := "nodes")
        ),
        SplitPane(
          vertical = true,
          initialPrimarySize = 230,
          className = "node-contents",
          onPrimarySizeChanged = (newSize) => {
            println(s"node-contents changed: $newSize")
          }
        )(
          SplitPane.Primary(
            nav(className := "cotonomas pane header-and-body")(
              paneToggle()
            )
          ),
          SplitPane.Secondary(
            slinky.web.html.main()(
              section(className := "flow pane")(
                paneToggle(),
                section(className := "timeline header-and-body")(
                )
              )
            )
          )
        )
      ),
      footer()
    )

  def paneToggle(): ReactElement =
    Fragment(
      button(className := "fold icon", title := "Fold")(
        span(className := "material-symbols")("arrow_left")
      ),
      button(className := "unfold icon", title := "Unfold")(
        span(className := "material-symbols")("arrow_right")
      )
    )
}
