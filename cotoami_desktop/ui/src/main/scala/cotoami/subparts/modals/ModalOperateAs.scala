package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{log_error, Context, Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.repositories.Domain
import cotoami.backend.{ErrorJson, InitialDataset}
import cotoami.components.materialSymbol
import cotoami.subparts.{spanNode, Modal}

object ModalOperateAs {

  case class Model(
      current: Node,
      switchingTo: Node,
      switching: Boolean = false,
      switchingError: Option[String] = None
  ) {
    def readyToSwitch: Boolean = !this.switching
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.OperateAsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Switch extends Msg
    case class Switched(result: Either[ErrorJson, InitialDataset]) extends Msg
  }

  def update(
      msg: Msg,
      model: Model,
      domain: Domain
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Switch =>
        (
          model.copy(switching = true, switchingError = None),
          InitialDataset.switchOperatingNodeTo(
            Option.when(!domain.nodes.isLocal(model.switchingTo.id))(
              model.switchingTo.id
            )
          ).map(Msg.Switched(_).into)
        )

      case Msg.Switched(Right(dataset)) =>
        (
          model.copy(switching = false, switchingError = None),
          Cmd.Batch(
            Browser.send(AppMsg.SetRemoteInitialDataset(dataset)),
            Modal.close(classOf[Modal.OperateAs])
          )
        )

      case Msg.Switched(Left(e)) =>
        (
          model.copy(
            switching = false,
            switchingError = Some(e.default_message)
          ),
          log_error(
            "Couldn't switch the operating node.",
            Some(js.JSON.stringify(e))
          )
        )
    }

  def apply(
      model: Model
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement = {
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
        sectionNode(model.current, "current"),
        div(className := "arrow")(materialSymbol("arrow_downward", "arrow")),
        sectionNode(model.switchingTo, "switching-to")
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType).into))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToSwitch,
          aria - "busy" := model.switching.toString(),
          onClick := (e => dispatch(Msg.Switch.into))
        )("Switch")
      )
    )
  }

  private def sectionNode(node: Node, elementClasses: String)(implicit
      context: Context
  ): ReactElement =
    section(className := elementClasses)(
      spanNode(node),
      Option.when(context.domain.nodes.isLocal(node.id)) {
        span(className := "is-local")("(local)")
      }
    )
}
