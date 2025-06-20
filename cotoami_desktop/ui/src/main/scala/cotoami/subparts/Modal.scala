package cotoami.subparts

import scala.util.chaining._
import scala.reflect.{classTag, ClassTag}
import slinky.core.facade.ReactElement
import slinky.web.html._
import com.softwaremill.quicklens._

import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.materialSymbol

import cotoami.{Context, Into, Model => AppModel, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Ito, Node}
import cotoami.repository.Root
import cotoami.subparts.modals._

sealed trait Modal

object Modal {

  /////////////////////////////////////////////////////////////////////////////
  // Stack of modals
  /////////////////////////////////////////////////////////////////////////////

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

    def clear: Stack = Stack()
  }

  /////////////////////////////////////////////////////////////////////////////
  // Modal models
  /////////////////////////////////////////////////////////////////////////////

  case class Confirm(model: ModalConfirm.Model) extends Modal
  object Confirm {
    def apply(message: ReactElement, msgOnConfirm: Into[AppMsg]): Confirm =
      Confirm(ModalConfirm.Model(message, msgOnConfirm.into))
  }

  case class Welcome(model: ModalWelcome.Model) extends Modal
  object Welcome {
    def apply(): (Welcome, Cmd[AppMsg]) = {
      val (model, cmd) = ModalWelcome.Model()
      (Welcome(model), cmd)
    }
  }

  case class InputPassword(model: ModalInputPassword.Model) extends Modal
  object InputPassword {
    def apply(
        msgOnSubmit: String => AppMsg,
        title: String,
        message: Option[String] = None,
        targetNode: Option[Node] = None
    ): InputPassword =
      InputPassword(
        ModalInputPassword.Model(msgOnSubmit, title, message, targetNode)
      )
  }

  case class NewPassword(model: ModalNewPassword.Model) extends Modal
  object NewPassword {
    def apply(
        title: String,
        message: String,
        principalNode: Option[Node],
        password: String
    ): NewPassword =
      NewPassword(
        ModalNewPassword.Model(title, message, principalNode, password)
      )

    def forOwner(password: String)(implicit
        context: Context
    ): NewPassword =
      NewPassword(
        context.i18n.text.ModalNewOwnerPassword_title,
        context.i18n.text.ModalNewOwnerPassword_message,
        context.repo.nodes.self,
        password
      )

    def forClient(node: Node, password: String)(implicit
        context: Context
    ): NewPassword =
      NewPassword(
        context.i18n.text.ModalNewClientPassword_title,
        context.i18n.text.ModalNewClientPassword_message,
        Some(node),
        password
      )
  }

  case class EditCoto(model: ModalEditCoto.Model) extends Modal
  object EditCoto {
    def apply(coto: Coto): (EditCoto, Cmd[AppMsg]) = {
      val (model, cmd) = ModalEditCoto.Model(coto)
      (EditCoto(model), cmd)
    }
  }

  case class Promote(model: ModalPromote.Model) extends Modal
  object Promote {
    def apply(coto: Coto): (Promote, Cmd[AppMsg]) = {
      val (model, cmd) = ModalPromote.Model(coto)
      (Promote(model), cmd)
    }
  }

  case class EditIto(model: ModalEditIto.Model) extends Modal
  object EditIto {
    def apply(ito: Ito): EditIto = EditIto(ModalEditIto.Model(ito))
  }

  case class Selection(model: ModalSelection.Model) extends Modal
  object Selection {
    def apply(enableClear: Boolean = true): Selection =
      Selection(ModalSelection.Model(enableClear))
  }

  case class NewIto(model: ModalNewIto.Model) extends Modal
  object NewIto {
    def apply(cotoId: Id[Coto]): NewIto = NewIto(ModalNewIto.Model(cotoId))
  }

  case class Subcoto(model: ModalSubcoto.Model) extends Modal
  object Subcoto {
    def apply(
        sourceCotoId: Id[Coto],
        order: Option[Int],
        defaultCotonomaId: Option[Id[Cotonoma]] = None
    )(implicit
        context: Context
    ): Subcoto =
      Subcoto(ModalSubcoto.Model(sourceCotoId, order, defaultCotonomaId))
  }

  case class Incorporate(
      model: ModalIncorporate.Model = ModalIncorporate.Model()
  ) extends Modal

  case class ParentSync(model: ModalParentSync.Model = ModalParentSync.Model())
      extends Modal

  case class SwitchNode(model: ModalSwitchNode.Model) extends Modal
  object SwitchNode {
    def apply(current: Node, switchingTo: Node): SwitchNode =
      SwitchNode(ModalSwitchNode.Model(current, switchingTo))
  }

  case class NodeProfile(model: ModalNodeProfile.Model) extends Modal
  object NodeProfile {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (NodeProfile, Cmd[AppMsg]) = {
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
    def apply(coto: Coto, repository: Root): Option[Repost] =
      ModalRepost.Model(coto, repository).map(Repost(_))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.ModalMsg(this)
  }

  object Msg {
    case class OpenModal(modal: Modal, cmd: Cmd[AppMsg] = Cmd.none) extends Msg
    object OpenModal {
      def apply(pair: (Modal, Cmd[AppMsg])): OpenModal =
        OpenModal(pair._1, pair._2)
    }
    case class CloseModal[M <: Modal](modalType: Class[M]) extends Msg

    case class ConfirmMsg(msg: ModalConfirm.Msg) extends Msg
    case class WelcomeMsg(msg: ModalWelcome.Msg) extends Msg
    case class InputPasswordMsg(msg: ModalInputPassword.Msg) extends Msg
    case class EditCotoMsg(msg: ModalEditCoto.Msg) extends Msg
    case class PromoteMsg(msg: ModalPromote.Msg) extends Msg
    case class EditItoMsg(msg: ModalEditIto.Msg) extends Msg
    case class SelectionMsg(msg: ModalSelection.Msg) extends Msg
    case class NewItoMsg(msg: ModalNewIto.Msg) extends Msg
    case class SubcotoMsg(msg: ModalSubcoto.Msg) extends Msg
    case class IncorporateMsg(msg: ModalIncorporate.Msg) extends Msg
    case class ParentSyncMsg(msg: ModalParentSync.Msg) extends Msg
    case class SwitchNodeMsg(msg: ModalSwitchNode.Msg) extends Msg
    case class NodeProfileMsg(msg: ModalNodeProfile.Msg) extends Msg
    case class NodeIconMsg(msg: ModalNodeIcon.Msg) extends Msg
    case class ClientsMsg(msg: ModalClients.Msg) extends Msg
    case class NewClientMsg(msg: ModalNewClient.Msg) extends Msg
    case class RepostMsg(msg: ModalRepost.Msg) extends Msg
  }

  def open(modal: Modal): Cmd.One[AppMsg] =
    Browser.send(Msg.OpenModal(modal).into)

  def open(pair: (Modal, Cmd[AppMsg])): Cmd.One[AppMsg] =
    Browser.send(Msg.OpenModal(pair).into)

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
            (updateModal(Confirm(modal), model), cmds)
          }
        }

      case Msg.WelcomeMsg(modalMsg) =>
        stack.get[Welcome].map { case Welcome(modal) =>
          ModalWelcome.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (updateModal(Welcome(modal), model), cmds)
          }
        }

      case Msg.InputPasswordMsg(modalMsg) =>
        stack.get[InputPassword].map { case InputPassword(modal) =>
          ModalInputPassword.update(modalMsg, modal).pipe {
            case (modal, cmds) =>
              (updateModal(InputPassword(modal), model), cmds)
          }
        }

      case Msg.EditCotoMsg(modalMsg) =>
        stack.get[EditCoto].map { case EditCoto(modal) =>
          ModalEditCoto.update(modalMsg, modal).pipe {
            case (modal, geomap, cmds) =>
              (
                updateModal(EditCoto(modal), model)
                  .modify(_.geomap).setTo(geomap),
                cmds
              )
          }
        }

      case Msg.PromoteMsg(modalMsg) =>
        stack.get[Promote].map { case Promote(modal) =>
          ModalPromote.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (updateModal(Promote(modal), model), cmds)
          }
        }

      case Msg.EditItoMsg(modalMsg) =>
        stack.get[EditIto].map { case EditIto(modal) =>
          ModalEditIto.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (updateModal(EditIto(modal), model), cmds)
          }
        }

      case Msg.SelectionMsg(modalMsg) =>
        stack.get[Selection].map { case Selection(modal) =>
          ModalSelection.update(modalMsg, modal).pipe {
            case (modal, cotos, cmds) =>
              (
                updateModal(Selection(modal), model)
                  .modify(_.repo.cotos).setTo(cotos),
                cmds
              )
          }
        }

      case Msg.NewItoMsg(modalMsg) =>
        stack.get[NewIto].map { case NewIto(modal) =>
          ModalNewIto.update(modalMsg, modal).pipe {
            case (modal, cotos, cmds) =>
              (
                updateModal(NewIto(modal), model)
                  .modify(_.repo.cotos).setTo(cotos),
                cmds
              )
          }
        }

      case Msg.SubcotoMsg(modalMsg) =>
        stack.get[Subcoto].map { case Subcoto(modal) =>
          ModalSubcoto.update(modalMsg, modal).pipe {
            case (modal, geomap, cmds) =>
              (
                updateModal(Subcoto(modal), model)
                  .modify(_.geomap).setTo(geomap),
                cmds
              )
          }
        }

      case Msg.IncorporateMsg(modalMsg) =>
        stack.get[Incorporate].map { case Incorporate(modal) =>
          ModalIncorporate.update(modalMsg, modal)
            .pipe { case (modal, nodes, cmds) =>
              (
                updateModal(Incorporate(modal), model)
                  .modify(_.repo.nodes).setTo(nodes),
                cmds
              )
            }
        }

      case Msg.ParentSyncMsg(modalMsg) =>
        stack.get[ParentSync].map { case ParentSync(modal) =>
          ModalParentSync.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (updateModal(ParentSync(modal), model), cmds)
          }
        }

      case Msg.SwitchNodeMsg(modalMsg) =>
        stack.get[SwitchNode].map { case SwitchNode(modal) =>
          ModalSwitchNode.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (updateModal(SwitchNode(modal), model), cmds)
          }
        }

      case Msg.NodeProfileMsg(modalMsg) =>
        stack.get[NodeProfile].map { case NodeProfile(modal) =>
          ModalNodeProfile.update(modalMsg, modal).pipe {
            case (modal, nodes, cmds) =>
              (
                updateModal(NodeProfile(modal), model)
                  .modify(_.repo.nodes).setTo(nodes),
                cmds
              )
          }
        }

      case Msg.NodeIconMsg(modalMsg) =>
        stack.get[NodeIcon].map { case NodeIcon(modal) =>
          ModalNodeIcon.update(modalMsg, modal).pipe {
            case (modal, nodes, cmds) =>
              (
                updateModal(NodeIcon(modal), model)
                  .modify(_.repo.nodes).setTo(nodes),
                cmds
              )
          }
        }

      case Msg.ClientsMsg(modalMsg) =>
        stack.get[Clients].map { case Clients(modal) =>
          ModalClients.update(modalMsg, modal).pipe { case (modal, cmds) =>
            (updateModal(Clients(modal), model), cmds)
          }
        }

      case Msg.NewClientMsg(modalMsg) =>
        stack.get[NewClient].map { case NewClient(modal) =>
          ModalNewClient.update(modalMsg, modal).pipe {
            case (modal, nodes, cmds) =>
              (
                updateModal(NewClient(modal), model)
                  .modify(_.repo.nodes).setTo(nodes),
                cmds
              )
          }
        }

      case Msg.RepostMsg(modalMsg) =>
        stack.get[Repost].map { case Repost(modal) =>
          ModalRepost.update(modalMsg, modal).pipe {
            case (modal, cotonomas, cmds) =>
              (
                updateModal(Repost(modal), model)
                  .modify(_.repo.cotonomas).setTo(cotonomas),
                cmds
              )
          }
        }
    }).getOrElse((model, Cmd.none))
  }

  private def updateModal[M <: Modal: ClassTag](
      newState: M,
      model: AppModel
  ): AppModel = model.copy(modalStack = model.modalStack.update(newState))

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

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

  def spanTitleIcon(iconName: String): ReactElement =
    span(className := "title-icon")(materialSymbol(iconName))

  def apply(
      model: AppModel
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    model.modalStack.top.flatMap {
      case Confirm(modal) => Some(ModalConfirm(modal))

      case Welcome(modal) =>
        model.systemInfo.map(info =>
          ModalWelcome(modal, info.recent_databases.toSeq)
        )

      case InputPassword(modal) => Some(ModalInputPassword(modal))

      case NewPassword(modal) => Some(ModalNewPassword(modal))

      case EditCoto(modal) => Some(ModalEditCoto(modal))

      case Promote(modal) => Some(ModalPromote(modal))

      case EditIto(modal) => Some(ModalEditIto(modal))

      case Selection(modal) => Some(ModalSelection(modal))

      case NewIto(modal) => Some(ModalNewIto(modal))

      case Subcoto(modal) => Some(ModalSubcoto(modal))

      case Incorporate(modal) => Some(ModalIncorporate(modal))

      case ParentSync(modal) => Some(ModalParentSync(modal, model.parentSync))

      case SwitchNode(modal) => Some(ModalSwitchNode(modal))

      case NodeProfile(modal) => Some(ModalNodeProfile(modal))

      case NodeIcon(modal) => Some(ModalNodeIcon(modal))

      case Clients(modal) => Some(ModalClients(modal))

      case NewClient(modal) => Some(ModalNewClient(modal))

      case Repost(modal) => Some(ModalRepost(modal))
    }
}
