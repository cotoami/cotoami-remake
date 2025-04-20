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

object ModalSwitchNode {

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
    def into = Modal.Msg.SwitchNodeMsg(this).pipe(AppMsg.ModalMsg)
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
          InitialDataset.switchSelfNodeTo(
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
            Modal.close(classOf[Modal.SwitchNode]),
            cotoami.info(
              "Switched the self node.",
              dataset.localNode.map(_.name)
            )
          )
        )

      case Msg.Switched(Left(e)) =>
        (
          model.copy(
            switching = false,
            switchingError = Some(e.default_message)
          ),
          cotoami.error("Couldn't switch the self node.", e)
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val modalType = classOf[Modal.SwitchNode]
    Modal.view(
      dialogClasses = "switch-node",
      closeButton = Some((modalType, dispatch)),
      error = model.switchingError
    )(
      Modal.spanTitleIcon(Node.SwitchIconName),
      context.i18n.text.ModalSwitchNode_title
    )(
      section(className := "preview")(
        p(context.i18n.text.ModalSwitchNode_message),
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
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := elementClasses)(
      PartsNode.spanNode(node),
      Option.when(context.repo.nodes.isLocal(node.id)) {
        span(className := "is-local")("(local)")
      }
    )
}
