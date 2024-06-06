package cotoami.subparts

import scala.util.chaining._
import slinky.core.facade.ReactElement
import com.softwaremill.quicklens._

import fui.Cmd

object Modal {
  sealed trait Model
  case class WelcomeModel(model: ModalWelcome.Model) extends Model
  case class AddNodeModel(model: ModalAddNode.Model) extends Model

  object Model {
    def welcome: Model = WelcomeModel(ModalWelcome.Model())
    def addNode: Model = AddNodeModel(ModalAddNode.Model())
  }

  case class Stack(modals: Seq[Model]) {
    def open(modal: Model): Stack = this.modify(_.modals).using(modal +: _)

    def top: Option[Model] = this.modals.headOption

    def updateTop(modal: Model): Stack =
      this.modify(_.modals).using(_.updated(0, modal))

    def closeTop: Stack = this.modify(_.modals).using(_.drop(1))
  }

  object Stack {
    def default: Stack = Stack(Seq(Model.welcome))
  }

  sealed trait Msg
  case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
  case class AddNodeMsg(msg: ModalAddNode.Msg) extends Msg

  def update(msg: Msg, stack: Stack): (Stack, Seq[Cmd[cotoami.Msg]]) =
    (msg, stack.top) match {
      case (WelcomeMsg(modalMsg), Some(WelcomeModel(modalModel))) =>
        ModalWelcome.update(modalMsg, modalModel)
          .pipe(pair => (stack.updateTop(WelcomeModel(pair._1)), pair._2))

      case (AddNodeMsg(modalMsg), Some(AddNodeModel(modalModel))) =>
        ModalAddNode.update(modalMsg, modalModel)
          .pipe(pair => (stack.updateTop(AddNodeModel(pair._1)), pair._2))

      case (_, _) => (stack, Seq.empty)
    }

  def apply(
      model: cotoami.Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    model.modalStack.top.map {
      case WelcomeModel(modalModel) =>
        model.systemInfo.map(info =>
          ModalWelcome(modalModel, info.recent_databases.toSeq, dispatch)
        )

      case AddNodeModel(modalModel) =>
        Some(ModalAddNode(modalModel, model.domain, dispatch))
    }
}
