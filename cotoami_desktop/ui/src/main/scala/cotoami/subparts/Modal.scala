package cotoami.subparts

import scala.util.chaining._
import scala.reflect.{classTag, ClassTag}
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

  case class Stack(modals: Seq[Model] = Seq.empty) {
    def open[M <: Model: ClassTag](modal: M): Stack =
      this.close[M].modify(_.modals).using(modal +: _)

    def top: Option[Model] = this.modals.headOption

    def get[M <: Model: ClassTag]: Option[M] =
      this.modals.find(classTag[M].runtimeClass.isInstance(_))
        .map(_.asInstanceOf[M])

    def update[M <: Model: ClassTag](newState: M): Stack =
      this.modify(_.modals).using(
        _.map(modal =>
          if (classTag[M].runtimeClass.isInstance(modal))
            newState
          else
            modal
        )
      )

    def closeTop: Stack = this.modify(_.modals).using(_.drop(1))

    def close[M <: Model: ClassTag]: Stack =
      this.modify(_.modals).using(
        _.filterNot(classTag[M].runtimeClass.isInstance(_))
      )
  }

  sealed trait Msg {
    def asAppMsg: cotoami.Msg = cotoami.ModalMsg(this)
  }
  case class OpenModal(modal: Model) extends Msg
  case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
  case class AddNodeMsg(msg: ModalAddNode.Msg) extends Msg

  def update(msg: Msg, stack: Stack): (Stack, Seq[Cmd[cotoami.Msg]]) =
    (msg match {
      case OpenModal(modal) =>
        Some((stack.open(modal), Seq.empty))

      case WelcomeMsg(modalMsg) =>
        stack.get[WelcomeModel].map { case WelcomeModel(modalModel) =>
          ModalWelcome.update(modalMsg, modalModel)
            .pipe { case (model, cmds) =>
              (stack.update(WelcomeModel(model)), cmds)
            }
        }

      case AddNodeMsg(modalMsg) =>
        stack.get[AddNodeModel].map { case AddNodeModel(modalModel) =>
          ModalAddNode.update(modalMsg, modalModel)
            .pipe { case (model, cmds) =>
              (stack.update(AddNodeModel(model)), cmds)
            }
        }
    }).getOrElse((stack, Seq.empty))

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
