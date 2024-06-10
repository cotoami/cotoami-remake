package cotoami.subparts

import scala.util.chaining._
import scala.reflect.{classTag, ClassTag}
import slinky.core.facade.ReactElement
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}

object Modal {
  sealed trait Model
  case class Welcome(model: ModalWelcome.Model = ModalWelcome.Model())
      extends Model
  case class AddNode(model: ModalAddNode.Model = ModalAddNode.Model())
      extends Model
  case class ParentSync() extends Model

  case class Stack(modals: Seq[Model] = Seq.empty) {
    def open[M <: Model: ClassTag](modal: M): Stack =
      this.close(modal.getClass()).modify(_.modals).using(modal +: _)

    def opened[M <: Model: ClassTag]: Boolean =
      this.modals.exists(classTag[M].runtimeClass.isInstance(_))

    def openIfNot[M <: Model: ClassTag](modal: M): Stack =
      if (this.opened[M]) this else this.open(modal)

    def get[M <: Model: ClassTag]: Option[M] =
      this.modals.find(classTag[M].runtimeClass.isInstance(_))
        .map(_.asInstanceOf[M])

    def top: Option[Model] = this.modals.headOption

    def update[M <: Model: ClassTag](newState: M): Stack =
      this.modify(_.modals).using(
        _.map(modal =>
          if (classTag[M].runtimeClass.isInstance(modal))
            newState
          else
            modal
        )
      )

    def close[M <: Model](modalType: Class[M]): Stack =
      this.modify(_.modals).using(_.filterNot(modalType.isInstance(_)))
  }

  sealed trait Msg {
    def asAppMsg: cotoami.Msg = cotoami.ModalMsg(this)
  }
  case class OpenModal(modal: Model) extends Msg
  case class CloseModal[M <: Model](modalType: Class[M]) extends Msg
  case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
  case class AddNodeMsg(msg: ModalAddNode.Msg) extends Msg

  def open(modal: Model): Cmd[cotoami.Msg] =
    Browser.send(OpenModal(modal).asAppMsg)

  def close[M <: Model](modalType: Class[M]): Cmd[cotoami.Msg] =
    Browser.send(Modal.CloseModal(modalType).asAppMsg)

  def update(msg: Msg, stack: Stack): (Stack, Seq[Cmd[cotoami.Msg]]) =
    (msg match {
      case OpenModal(modal) =>
        Some((stack.open(modal), Seq.empty))

      case CloseModal(modalType) =>
        Some((stack.close(modalType), Seq.empty))

      case WelcomeMsg(modalMsg) =>
        stack.get[Welcome].map { case Welcome(modalModel) =>
          ModalWelcome.update(modalMsg, modalModel)
            .pipe { case (model, cmds) =>
              (stack.update(Welcome(model)), cmds)
            }
        }

      case AddNodeMsg(modalMsg) =>
        stack.get[AddNode].map { case AddNode(modalModel) =>
          ModalAddNode.update(modalMsg, modalModel)
            .pipe { case (model, cmds) =>
              (stack.update(AddNode(model)), cmds)
            }
        }
    }).getOrElse((stack, Seq.empty))

  def apply(
      model: cotoami.Model,
      dispatch: cotoami.Msg => Unit
  ): ReactElement =
    model.modalStack.top.map {
      case Welcome(modalModel) =>
        model.systemInfo.map(info =>
          ModalWelcome(modalModel, info.recent_databases.toSeq, dispatch)
        )

      case AddNode(modalModel) =>
        Some(ModalAddNode(modalModel, model.domain, dispatch))

      case ParentSync() =>
        Some(ModalParentSync(model.parentSync, model.domain, dispatch))
    }
}
