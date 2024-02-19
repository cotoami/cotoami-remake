package cotoami

import scala.scalajs.js
import scala.scalajs.LinkingInfo
import org.scalajs.dom
import org.scalajs.dom.URL

import slinky.core._
import slinky.core.facade.{ReactElement, Fragment}
import slinky.hot
import slinky.web.html._

import cotoami.FunctionalUI._

object Main {

  //
  // MODEL
  //

  case class Model(messages: Seq[String] = Seq.empty, input: String = "")

  def init(url: URL): (Model, Seq[Cmd[Msg]]) = (Model(), Seq.empty)

  //
  // UPDATE
  //

  sealed trait Msg
  case class Input(input: String) extends Msg
  case object Send extends Msg

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

  //
  // VIEW
  //

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
        div(className := "node-contents")(
          nav(className := "cotonomas pane header-and-body")(
            paneToggle()
          ),
          slinky.web.html.main()(
            section(className := "flow pane")(
              paneToggle(),
              section(className := "timeline header-and-body")(
                SplitPane(
                  split = "vertical",
                  initialPrimarySize = 200,
                  className = "foo"
                )(
                  SplitPane.Primary(
                    div()("1")
                  ),
                  SplitPane.Secondary(
                    SplitPane(
                      split = "vertical",
                      initialPrimarySize = 100,
                      className = "bar"
                    )(
                      SplitPane.Primary(
                        div()("2")
                      ),
                      SplitPane.Secondary(
                        div()("3")
                      )
                    )
                  )
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

  def main(args: Array[String]): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    Browser.runProgram(
      dom.document.getElementById("app"),
      Program(init, view, update)
    )
  }
}
