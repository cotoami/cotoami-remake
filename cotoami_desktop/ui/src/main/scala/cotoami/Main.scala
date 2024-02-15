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
            src := "/images/logo/logomark.svg",
            aria - "label" := "View app info"
          )
        )
      ),
      div(id := "app-body", className := "body"),
      footer()
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
