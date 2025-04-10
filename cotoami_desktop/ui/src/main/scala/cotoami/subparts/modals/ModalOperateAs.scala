package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.materialSymbol

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.backend.{ErrorJson, InitialDataset}
import cotoami.subparts.{Modal, PartsNode}

object ModalOperateAs {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      current: Node,
      switchingTo: Node,
      switching: Boolean = false,
      switchingError: Option[String] = None
  ) {
    def readyToSwitch: Boolean = !switching
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.OperateAsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Switch extends Msg
    case class Switched(result: Either[ErrorJson, InitialDataset]) extends Msg
  }

  def update(
      msg: Msg,
      model: Model
  )(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Switch =>
        (
          model.copy(switching = true, switchingError = None),
          InitialDataset.switchOperatedNodeTo(
            Option.when(!context.repo.nodes.isLocal(model.switchingTo.id))(
              model.switchingTo.id
            )
          ).map(Msg.Switched(_).into)
        )

      case Msg.Switched(Right(dataset)) =>
        (
          model.copy(switching = false, switchingError = None),
          Cmd.Batch(
            Browser.send(AppMsg.SetInitialDataset(dataset)),
            Modal.close(classOf[Modal.OperateAs])
          )
        )

      case Msg.Switched(Left(e)) =>
        (
          model.copy(
            switching = false,
            switchingError = Some(e.default_message)
          ),
          cotoami.error("Couldn't switch the operated node.", e)
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.OperateAs]
    Modal.view(
      dialogClasses = "operate-as",
      closeButton = Some((modalType, dispatch)),
      error = model.switchingError
    )(
      Modal.spanTitleIcon(Node.SwitchIconName),
      "Switch Node"
    )(
      section(className := "preview")(
        p("You are about to switch the node to operate on as below:"),
        sectionNode(model.current, "current"),
        div(className := "arrow")(materialSymbol("arrow_downward", "arrow")),
        sectionNode(model.switchingTo, "switching-to")
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToSwitch,
          aria - "busy" := model.switching.toString(),
          onClick := (e => dispatch(Msg.Switch))
        )("Switch")
      )
    )
  }

  private def sectionNode(node: Node, elementClasses: String)(implicit
      context: Context
  ): ReactElement =
    section(className := elementClasses)(
      PartsNode.spanNode(node),
      Option.when(context.repo.nodes.isLocal(node.id)) {
        span(className := "is-local")("(local)")
      }
    )
}
