package cotoami.subparts

import scala.util.chaining._
import scala.reflect.{classTag, ClassTag}
import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.{Context, Model => AppModel, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.subparts.modals._

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

  case class NodeProfile(model: ModalNodeProfile.Model) extends Model
  object NodeProfile {
    def apply(nodeId: Id[Node]): (NodeProfile, Seq[Cmd[AppMsg]]) =
      ModalNodeProfile.Model(nodeId).pipe(r => (NodeProfile(r._1), r._2))
  }

  case class NodeIcon(model: ModalNodeIcon.Model = ModalNodeIcon.Model())
      extends Model

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
    case class OpenModal(modal: Model, cmds: Seq[Cmd[AppMsg]] = Seq.empty)
        extends Msg
    case class CloseModal[M <: Model](modalType: Class[M]) extends Msg

    case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
    case class IncorporateMsg(msg: ModalIncorporate.Msg) extends Msg
    case class ParentSyncMsg(msg: ModalParentSync.Msg) extends Msg
    case class OperateAsMsg(msg: ModalOperateAs.Msg) extends Msg
    case class NodeProfileMsg(msg: ModalNodeProfile.Msg) extends Msg
    case class NodeIconMsg(msg: ModalNodeIcon.Msg) extends Msg
  }

  def open(modal: Model): Cmd[AppMsg] =
    Browser.send(Msg.OpenModal(modal).toApp)

  def close[M <: Model](modalType: Class[M]): Cmd[AppMsg] =
    Browser.send(Msg.CloseModal(modalType).toApp)

  def update(msg: Msg, model: AppModel)(implicit
      context: Context
  ): (AppModel, Seq[Cmd[AppMsg]]) = {
    val stack = model.modalStack
    (msg match {
      case Msg.OpenModal(modal, cmds) =>
        Some((model.modify(_.modalStack).using(_.open(modal)), cmds))

      case Msg.CloseModal(modalType) =>
        Some((model.modify(_.modalStack).using(_.close(modalType)), Seq.empty))

      case Msg.WelcomeMsg(modalMsg) =>
        stack.get[Welcome].map { case Welcome(modalModel) =>
          ModalWelcome.update(modalMsg, modalModel).pipe { case (modal, cmds) =>
            (model.updateModal(Welcome(modal)), cmds)
          }
        }

      case Msg.IncorporateMsg(modalMsg) =>
        stack.get[Incorporate].map { case Incorporate(modalModel) =>
          ModalIncorporate.update(modalMsg, modalModel)
            .pipe { case (modal, nodes, cmds) =>
              (
                model
                  .updateModal(Incorporate(modal))
                  .modify(_.domain.nodes).setTo(nodes),
                cmds
              )
            }
        }

      case Msg.ParentSyncMsg(modalMsg) =>
        stack.get[ParentSync].map { case ParentSync(modalModel) =>
          ModalParentSync.update(modalMsg, modalModel).pipe {
            case (modal, cmds) => (model.updateModal(ParentSync(modal)), cmds)
          }
        }

      case Msg.OperateAsMsg(modalMsg) =>
        stack.get[OperateAs].map { case OperateAs(modalModel) =>
          ModalOperateAs.update(modalMsg, modalModel, model.domain).pipe {
            case (modal, cmds) => (model.updateModal(OperateAs(modal)), cmds)
          }
        }

      case Msg.NodeProfileMsg(modalMsg) =>
        stack.get[NodeProfile].map { case NodeProfile(modalModel) =>
          ModalNodeProfile.update(modalMsg, modalModel).pipe {
            case (modal, domain, cmds) =>
              (
                model
                  .updateModal(NodeProfile(modal))
                  .modify(_.domain).setTo(domain),
                cmds
              )
          }
        }

      case Msg.NodeIconMsg(modalMsg) =>
        stack.get[NodeIcon].map { case NodeIcon(modalModel) =>
          ModalNodeIcon.update(modalMsg, modalModel).pipe {
            case (modal, nodes, cmds) =>
              (
                model
                  .updateModal(NodeIcon(modal))
                  .modify(_.domain.nodes).setTo(nodes),
                cmds
              )
          }
        }
    }).getOrElse((model, Seq.empty))
  }

  def apply(
      model: AppModel
  )(implicit context: Context, dispatch: AppMsg => Unit): ReactElement =
    model.modalStack.top.flatMap {
      case Welcome(modalModel) =>
        model.systemInfo.map(info =>
          ModalWelcome(modalModel, info.recent_databases.toSeq)
        )

      case Incorporate(modalModel) =>
        Some(ModalIncorporate(modalModel))

      case ParentSync(modalModel) =>
        Some(ModalParentSync(modalModel, model.parentSync))

      case OperateAs(modalModel) =>
        Some(ModalOperateAs(modalModel))

      case NodeProfile(modalModel) =>
        Some(ModalNodeProfile(modalModel))

      case NodeIcon(modalModel) =>
        Some(ModalNodeIcon(modalModel))
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
          h1()(title: _*)
        ),
        error.map(e => section(className := "error")(e)),
        div(className := "body")(body: _*)
      )
    )
}
