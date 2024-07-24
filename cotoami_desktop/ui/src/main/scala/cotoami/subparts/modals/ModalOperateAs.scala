package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{log_error, Context, Msg => AppMsg}
import cotoami.backend.{ErrorJson, InitialDataset, Node}
import cotoami.repositories.Domain
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

  sealed trait Msg {
    def toApp: AppMsg = Modal.Msg.OperateAsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.OperateAsMsg andThen AppMsg.ModalMsg

    case object Switch extends Msg
    case class Switched(result: Either[ErrorJson, InitialDataset]) extends Msg
  }

  def update(
      msg: Msg,
      model: Model,
      domain: Domain
  ): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.Switch =>
        (
          model.copy(switching = true, switchingError = None),
          Seq(
            InitialDataset.switchOperatingNodeTo(
              Option.when(!domain.nodes.isLocal(model.switchingTo.id))(
                model.switchingTo.id
              )
            ).map(Msg.toApp(Msg.Switched(_)))
          )
        )

      case Msg.Switched(Right(dataset)) =>
        (
          model.copy(switching = false, switchingError = None),
          Seq(
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
  )(implicit context: Context): ReactElement = {
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
          onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType).toApp))
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToSwitch,
          aria - "busy" := model.switching.toString(),
          onClick := (e => dispatch(Msg.Switch.toApp))
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
