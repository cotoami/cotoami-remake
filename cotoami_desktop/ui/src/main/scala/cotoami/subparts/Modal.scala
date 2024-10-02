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

sealed trait Modal

object Modal {
  case class Welcome(model: ModalWelcome.Model = ModalWelcome.Model())
      extends Modal

  case class Incorporate(
      model: ModalIncorporate.Model = ModalIncorporate.Model()
  ) extends Modal

  case class ParentSync(model: ModalParentSync.Model = ModalParentSync.Model())
      extends Modal

  case class OperateAs(model: ModalOperateAs.Model) extends Modal
  object OperateAs {
    def apply(current: Node, switchingTo: Node): OperateAs =
      OperateAs(ModalOperateAs.Model(current, switchingTo))
  }

  case class NodeProfile(model: ModalNodeProfile.Model) extends Modal
  object NodeProfile {
    def apply(nodeId: Id[Node]): (NodeProfile, Cmd[AppMsg]) = {
      val (model, cmd) = ModalNodeProfile.Model(nodeId)
      (NodeProfile(model), cmd)
    }
  }

  case class NodeIcon(model: ModalNodeIcon.Model = ModalNodeIcon.Model())
      extends Modal

  case class Clients(model: ModalClients.Model) extends Modal
  object Clients {
    def apply(): (Clients, Cmd[AppMsg]) = {
      val (model, cmd) = ModalClients.Model()
      (Clients(model), cmd)
    }
  }

  case class Stack(modals: Seq[Modal] = Seq.empty) {
    def open[M <: Modal: ClassTag](modal: M): Stack =
      close(modal.getClass()).modify(_.modals).using(modal +: _)

    def opened[M <: Modal: ClassTag]: Boolean =
      modals.exists(classTag[M].runtimeClass.isInstance(_))

    def openIfNot[M <: Modal: ClassTag](modal: M): Stack =
      if (opened[M]) this else open(modal)

    def get[M <: Modal: ClassTag]: Option[M] =
      modals.find(classTag[M].runtimeClass.isInstance(_))
        .map(_.asInstanceOf[M])

    def top: Option[Modal] = modals.headOption

    def update[M <: Modal: ClassTag](newState: M): Stack =
      this.modify(_.modals).using(
        _.map(modal =>
          if (classTag[M].runtimeClass.isInstance(modal))
            newState
          else
            modal
        )
      )

    def close[M <: Modal](modalType: Class[M]): Stack =
      this.modify(_.modals).using(_.filterNot(modalType.isInstance(_)))
  }

  sealed trait Msg {
    def toApp: AppMsg = AppMsg.ModalMsg(this)
  }

  object Msg {
    case class OpenModal(modal: Modal, cmd: Cmd[AppMsg] = Cmd.none) extends Msg
    case class CloseModal[M <: Modal](modalType: Class[M]) extends Msg

    case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
    case class IncorporateMsg(msg: ModalIncorporate.Msg) extends Msg
    case class ParentSyncMsg(msg: ModalParentSync.Msg) extends Msg
    case class OperateAsMsg(msg: ModalOperateAs.Msg) extends Msg
    case class NodeProfileMsg(msg: ModalNodeProfile.Msg) extends Msg
    case class NodeIconMsg(msg: ModalNodeIcon.Msg) extends Msg
    case class ClientsMsg(msg: ModalClients.Msg) extends Msg
  }

  def open(modal: Modal): Cmd.One[AppMsg] =
    Browser.send(Msg.OpenModal(modal).toApp)

  def close[M <: Modal](modalType: Class[M]): Cmd.One[AppMsg] =
    Browser.send(Msg.CloseModal(modalType).toApp)

  def update(msg: Msg, model: AppModel)(implicit
      context: Context
  ): (AppModel, Cmd[AppMsg]) = {
    val stack = model.modalStack
    (msg match {
      case Msg.OpenModal(modal, cmd) =>
        Some((model.modify(_.modalStack).using(_.open(modal)), cmd))

      case Msg.CloseModal(modalType) =>
        Some((model.modify(_.modalStack).using(_.close(modalType)), Cmd.none))

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
            case (modal, cmds) => (model.updateModal(NodeProfile(modal)), cmds)
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

      case Msg.ClientsMsg(modalMsg) =>
        stack.get[Clients].map { case Clients(modalModel) =>
          ModalClients.update(modalMsg, modalModel).pipe { case (modal, cmds) =>
            (model.updateModal(Clients(modal)), cmds)
          }
        }
    }).getOrElse((model, Cmd.none))
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

      case Clients(modalModel) =>
        Some(ModalClients(modalModel))
    }

  def view[M <: Modal](
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
