package cotoami.subparts

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{log_error, Msg => AppMsg}
import cotoami.backend.{ErrorJson, InitialDatasetJson, Node}
import cotoami.components.materialSymbol
import cotoami.backend.InitialDataset

object ModalOperateAs {

  case class Model(
      current: Node,
      switchingTo: Node,
      switching: Boolean = false,
      switchingError: Option[String] = None
  )

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.OperateAsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Switch extends Msg
    case class Switched(result: Either[ErrorJson, InitialDatasetJson])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.Switch =>
        (
          model.copy(switching = true, switchingError = None),
          Seq.empty
        )

      case Msg.Switched(Right(json)) =>
        (
          model.copy(switching = false, switchingError = None),
          Seq(
            Browser.send(AppMsg.SetRemoteInitialDataset(InitialDataset(json))),
            Modal.close(classOf[Modal.OperateAs])
          )
        )

      case Msg.Switched(Left(e)) =>
        (
          model.copy(
            switching = false,
            switchingError = Some(e.default_message)
          ),
          Seq(
            log_error(
              "Couldn't switch the operating node.",
              Some(js.JSON.stringify(e))
            )
          )
        )
    }

  def apply(
      model: Model,
      dispatch: AppMsg => Unit
  ): ReactElement = {
    val modalType = classOf[Modal.OperateAs]
    Modal.view(
      elementClasses = "operate-as",
      closeButton = Some((modalType, dispatch)),
      error = model.switchingError
    )(
      "Switch Operating Node"
    )(
      section(className := "preview")(
        p("You are about to switch the operating node as below:"),
        section(className := "current")(
          spanNode(model.current)
        ),
        div(className := "arrow")(materialSymbol("arrow_downward", "arrow")),
        section(className := "switching-to")(
          spanNode(model.switchingTo)
        )
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType).toApp))
        )("Cancel"),
        button(
          `type` := "button",
          aria - "busy" := model.switching.toString()
        )("Switch")
      )
    )
  }
}
