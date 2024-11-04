package cotoami.subparts

import scala.util.chaining._
import scala.reflect.{classTag, ClassTag}
import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import fui.{Browser, Cmd}
import cotoami.{Context, Into, Model => AppModel, Msg => AppMsg}
import cotoami.models.{Coto, Id, Node}
import cotoami.subparts.modals._

sealed trait Modal

object Modal {

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

  case class Confirm(model: ModalConfirm.Model) extends Modal
  object Confirm {
    def apply(message: String, msgOnConfirm: Into[AppMsg]): Confirm =
      Confirm(ModalConfirm.Model(message, msgOnConfirm.into))
  }

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

  case class NewClient(model: ModalNewClient.Model = ModalNewClient.Model())
      extends Modal

  case class Repost(model: ModalRepost.Model) extends Modal
  object Repost {
    def apply(cotoId: Id[Coto]): Repost = Repost(ModalRepost.Model(cotoId))
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.ModalMsg(this)
  }

  object Msg {
    case class OpenModal(modal: Modal, cmd: Cmd[AppMsg] = Cmd.none) extends Msg
    case class CloseModal[M <: Modal](modalType: Class[M]) extends Msg

    case class ConfirmMsg(msg: ModalConfirm.Msg) extends Msg
    case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
    case class IncorporateMsg(msg: ModalIncorporate.Msg) extends Msg
    case class ParentSyncMsg(msg: ModalParentSync.Msg) extends Msg
    case class OperateAsMsg(msg: ModalOperateAs.Msg) extends Msg
    case class NodeProfileMsg(msg: ModalNodeProfile.Msg) extends Msg
    case class NodeIconMsg(msg: ModalNodeIcon.Msg) extends Msg
    case class ClientsMsg(msg: ModalClients.Msg) extends Msg
    case class NewClientMsg(msg: ModalNewClient.Msg) extends Msg
    case class RepostMsg(msg: ModalRepost.Msg) extends Msg
  }

  def open(modal: Modal): Cmd.One[AppMsg] =
    Browser.send(Msg.OpenModal(modal).into)

  def close[M <: Modal](modalType: Class[M]): Cmd.One[AppMsg] =
    Browser.send(Msg.CloseModal(modalType).into)

  def update(msg: Msg, model: AppModel)(implicit
      context: Context
  ): (AppModel, Cmd[AppMsg]) = {
    val stack = model.modalStack
    (msg match {
      case Msg.OpenModal(modal, cmd) =>
        Some((model.modify(_.modalStack).using(_.open(modal)), cmd))

      case Msg.CloseModal(modalType) =>
        Some((model.modify(_.modalStack).using(_.close(modalType)), Cmd.none))

      case Msg.ConfirmMsg(modalMsg) =>
        stack.get[Confirm].map { case Confirm(modal) =>
          ModalConfirm.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (model.updateModal(Confirm(modal)), cmds)
          }
        }

      case Msg.WelcomeMsg(modalMsg) =>
        stack.get[Welcome].map { case Welcome(modal) =>
          ModalWelcome.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (model.updateModal(Welcome(modal)), cmds)
          }
        }

      case Msg.IncorporateMsg(modalMsg) =>
        stack.get[Incorporate].map { case Incorporate(modal) =>
          ModalIncorporate.update(modalMsg, modal)
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
        stack.get[ParentSync].map { case ParentSync(modal) =>
          ModalParentSync.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (model.updateModal(ParentSync(modal)), cmds)
          }
        }

      case Msg.OperateAsMsg(modalMsg) =>
        stack.get[OperateAs].map { case OperateAs(modal) =>
          ModalOperateAs.update(modalMsg, modal, model.domain).pipe {
            case (modal, cmds) => (model.updateModal(OperateAs(modal)), cmds)
          }
        }

      case Msg.NodeProfileMsg(modalMsg) =>
        stack.get[NodeProfile].map { case NodeProfile(modal) =>
          ModalNodeProfile.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (model.updateModal(NodeProfile(modal)), cmds)
          }
        }

      case Msg.NodeIconMsg(modalMsg) =>
        stack.get[NodeIcon].map { case NodeIcon(modal) =>
          ModalNodeIcon.update(modalMsg, modal).pipe {
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
        stack.get[Clients].map { case Clients(modal) =>
          ModalClients.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (model.updateModal(Clients(modal)), cmds)
          }
        }

      case Msg.NewClientMsg(modalMsg) =>
        stack.get[NewClient].map { case NewClient(modal) =>
          ModalNewClient.update(modalMsg, modal).pipe {
            case (modal, nodes, cmds) =>
              (
                model
                  .updateModal(NewClient(modal))
                  .modify(_.domain.nodes).setTo(nodes),
                cmds
              )
          }
        }

      case Msg.RepostMsg(modalMsg) =>
        stack.get[Repost].map { case Repost(modal) =>
          ModalRepost.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (model.updateModal(Repost(modal)), cmds)
          }
        }
    }).getOrElse((model, Cmd.none))
  }

  def apply(
      model: AppModel
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    model.modalStack.top.flatMap {
      case Confirm(modal) => Some(ModalConfirm(modal))

      case Welcome(modal) =>
        model.systemInfo.map(info =>
          ModalWelcome(modal, info.recent_databases.toSeq)
        )

      case Incorporate(modal) => Some(ModalIncorporate(modal))

      case ParentSync(modal) => Some(ModalParentSync(modal, model.parentSync))

      case OperateAs(modal) => Some(ModalOperateAs(modal))

      case NodeProfile(modal) => Some(ModalNodeProfile(modal))

      case NodeIcon(modal) => Some(ModalNodeIcon(modal))

      case Clients(modal) => Some(ModalClients(modal))

      case NewClient(modal) => Some(ModalNewClient(modal))

      case Repost(modal) => Some(ModalRepost(modal))
    }

  def view[M <: Modal](
      dialogClasses: String,
      closeButton: Option[(Class[M], Into[AppMsg] => Unit)] = None,
      error: Option[String] = None,
      bodyClasses: String = ""
  )(title: ReactElement*)(body: ReactElement*): ReactElement =
    dialog(
      className := dialogClasses,
      slinky.web.html.open := true,
      data - "tauri-drag-region" := "default"
    )(
      article()(
        header()(
          closeButton.map { case (modalType, dispatch) =>
            button(
              className := "close default",
              onClick := (_ => dispatch(Modal.Msg.CloseModal(modalType)))
            )
          },
          h1()(title: _*)
        ),
        error.map(e => section(className := "error")(e)),
        div(className := s"modal-body ${bodyClasses}")(body: _*)
      )
    )
}
