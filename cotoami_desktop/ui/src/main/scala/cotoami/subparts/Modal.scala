package cotoami.subparts

import scala.util.chaining._
import scala.reflect.{classTag, ClassTag}
import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.{Model => AppModel, Msg => AppMsg}
import cotoami.backend.Node
import cotoami.models.Context
import cotoami.repositories.Domain

object Modal {
  sealed trait Model
  case class Welcome(model: ModalWelcome.Model = ModalWelcome.Model())
      extends Model
  case class Incorporate(
      model: ModalIncorporate.Model = ModalIncorporate.Model()
  ) extends Model
  case class ParentSync(model: ModalParentSync.Model = ModalParentSync.Model())
      extends Model
  case class OperateAs(model: ModalOperateAs.Model) extends Model
  object OperateAs {
    def apply(current: Node, switchingTo: Node): OperateAs =
      OperateAs(ModalOperateAs.Model(current, switchingTo))
  }

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
    def toApp: AppMsg = AppMsg.ModalMsg(this)
  }

  object Msg {
    case class OpenModal(modal: Model) extends Msg
    case class CloseModal[M <: Model](modalType: Class[M]) extends Msg

    case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
    case class IncorporateMsg(msg: ModalIncorporate.Msg) extends Msg
    case class ParentSyncMsg(msg: ModalParentSync.Msg) extends Msg
    case class OperateAsMsg(msg: ModalOperateAs.Msg) extends Msg
  }

  def open(modal: Model): Cmd[AppMsg] =
    Browser.send(Msg.OpenModal(modal).toApp)

  def close[M <: Model](modalType: Class[M]): Cmd[AppMsg] =
    Browser.send(Msg.CloseModal(modalType).toApp)

  def update(
      msg: Msg,
      model: AppModel
  ): (AppModel, Seq[Cmd[AppMsg]]) = {
    val stack = model.modalStack
    (msg match {
      case Msg.OpenModal(modal) =>
        Some((model.modify(_.modalStack).using(_.open(modal)), Seq.empty))

      case Msg.CloseModal(modalType) =>
        Some((model.modify(_.modalStack).using(_.close(modalType)), Seq.empty))

      case Msg.WelcomeMsg(modalMsg) =>
        stack.get[Welcome].map { case Welcome(modalModel) =>
          ModalWelcome.update(modalMsg, modalModel)
            .pipe { case (modal, cmds) =>
              (model.copy(modalStack = stack.update(Welcome(modal))), cmds)
            }
        }

      case Msg.IncorporateMsg(modalMsg) =>
        stack.get[Incorporate].map { case Incorporate(modalModel) =>
          ModalIncorporate.update(modalMsg, modalModel, model.domain.nodes)
            .pipe { case (modal, nodes, cmds) =>
              (
                model
                  .modify(_.modalStack).using(_.update(Incorporate(modal)))
                  .modify(_.domain.nodes).setTo(nodes),
                cmds
              )
            }
        }

      case Msg.ParentSyncMsg(modalMsg) =>
        stack.get[ParentSync].map { case ParentSync(modalModel) =>
          ModalParentSync.update(modalMsg, modalModel)
            .pipe { case (modal, cmds) =>
              (model.copy(modalStack = stack.update(ParentSync(modal))), cmds)
            }
        }

      case Msg.OperateAsMsg(modalMsg) =>
        stack.get[OperateAs].map { case OperateAs(modalModel) =>
          ModalOperateAs.update(modalMsg, modalModel, model.domain)
            .pipe { case (modal, cmds) =>
              (model.copy(modalStack = stack.update(OperateAs(modal))), cmds)
            }
        }
    }).getOrElse((model, Seq.empty))
  }

  def apply(
      model: AppModel,
      dispatch: AppMsg => Unit
  )(implicit context: Context, domain: Domain): ReactElement =
    model.modalStack.top.map {
      case Welcome(modalModel) =>
        model.systemInfo.map(info =>
          ModalWelcome(modalModel, info.recent_databases.toSeq, dispatch)
        )

      case Incorporate(modalModel) =>
        Some(ModalIncorporate(modalModel, dispatch))

      case ParentSync(modalModel) =>
        Some(ModalParentSync(modalModel, model.parentSync, dispatch))

      case OperateAs(modalModel) =>
        Some(ModalOperateAs(modalModel, dispatch))
    }

  def view[M <: Model](
      elementClasses: String,
      closeButton: Option[(Class[M], AppMsg => Unit)] = None,
      error: Option[String] = None
  )(title: ReactElement*)(body: ReactElement*): ReactElement =
    dialog(
      className := elementClasses,
      slinky.web.html.open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          closeButton.map { case (modalType, dispatch) =>
            button(
              className := "close default",
              onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType).toApp))
            )
          },
          h1()(title)
        ),
        error.map(e => section(className := "error")(e)),
        div(className := "body")(body: _*)
      )
    )
}
