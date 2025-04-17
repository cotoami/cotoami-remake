package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Coto, Id, Node, Server}
import cotoami.repository.Root
import cotoami.backend.{DatabaseInfo, ErrorJson}
import cotoami.subparts.{
  buttonEdit,
  field,
  fieldInput,
  Modal,
  PartsCoto,
  PartsNode
}

object ModalNodeProfile {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      error: Option[String] = None,
      resettingPassword: Boolean = false,
      resettingPasswordError: Option[String] = None,

      // Roles
      localServer: SectionLocalServer.Model,
      asClient: SectionAsClient.Model,
      asChild: SectionAsChild.Model
  ) {
    def isLocal(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isSelf(implicit context: Context): Boolean =
      context.repo.nodes.isSelf(nodeId)

    def asServer(implicit context: Context): Option[Server] =
      context.repo.nodes.servers.get(nodeId)

    def nodeRoleName(implicit context: Context): Option[String] =
      if (isSelf)
        Some(
          context.i18n.text.ModalNodeProfile_selfNode ++
            Option.when(!isLocal)(" (switched)").getOrElse("")
        )
      else if (asServer.isDefined)
        Some(context.i18n.text.Server)
      else if (asClient.client.isDefined)
        Some(context.i18n.text.Client)
      else
        None
  }

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) = {
      val (localServer, localServerCmd) = SectionLocalServer.Model(nodeId)
      val (asClient, asClientCmd) = SectionAsClient.Model(nodeId)
      val (asChild, asChildCmd) = SectionAsChild.Model(nodeId)
      (
        Model(
          nodeId = nodeId,
          localServer = localServer,
          asClient = asClient,
          asChild = asChild
        ),
        Root.fetchNodeDetails(nodeId) ++
          localServerCmd ++
          asClientCmd ++
          asChildCmd
      )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object ResetOwnerPassword extends Msg
    case class OwnerPasswordReset(result: Either[ErrorJson, String]) extends Msg

    // Roles
    case class SectionLocalServerMsg(submsg: SectionLocalServer.Msg) extends Msg
    case class SectionAsClientMsg(submsg: SectionAsClient.Msg) extends Msg
    case class SectionAsChildMsg(submsg: SectionAsChild.Msg) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.ResetOwnerPassword =>
        (
          model.copy(resettingPassword = true),
          DatabaseInfo.newOwnerPassword
            .map(Msg.OwnerPasswordReset(_).into)
        )

      case Msg.OwnerPasswordReset(Right(password)) =>
        (
          model.copy(resettingPassword = false),
          Modal.open(Modal.NewPassword.forOwner(password))
        )

      case Msg.OwnerPasswordReset(Left(e)) =>
        (
          model.copy(
            resettingPassword = false,
            resettingPasswordError = Some(e.default_message)
          ),
          Cmd.none
        )

      case Msg.SectionLocalServerMsg(submsg) => {
        val (localServer, cmd) =
          SectionLocalServer.update(submsg, model.localServer)
        (model.copy(localServer = localServer), cmd)
      }

      case Msg.SectionAsClientMsg(submsg) => {
        val (asClient, cmd) = SectionAsClient.update(submsg, model.asClient)
        (model.copy(asClient = asClient), cmd)
      }

      case Msg.SectionAsChildMsg(submsg) => {
        val (asChild, cmd) = SectionAsChild.update(submsg, model.asChild)
        (model.copy(asChild = asChild), cmd)
      }
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "node-profile",
      closeButton = Some((classOf[Modal.NodeProfile], dispatch)),
      error = model.error
    )(
      Modal.spanTitleIcon(Node.IconName),
      context.i18n.text.ModalNodeProfile_title
    )(
      context.repo.nodes.get(model.nodeId)
        .map(modalContent(_, model))
        .getOrElse(s"Node ${model.nodeId} not found.")
    )

  private def modalContent(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val rootCoto = context.repo.rootOf(model.nodeId).map(_._2)
    Fragment(
      divSidebar(node, model),
      div(className := "main")(
        sectionToolButtons(node, model),
        div(className := "fields")(
          ScrollArea(className = Some("scroll-fields"))(
            fieldId(node),
            fieldName(node, rootCoto, model),
            rootCoto.map(fieldDescription(_, model)),
            Option.when(model.isLocal && model.isSelf) {
              fieldOwnerPassword(model)
            },
            SectionLocalServer(model.localServer),
            model.asServer.map(SectionAsServer(_)),
            SectionAsClient(model.asClient),
            SectionAsChild(model.asChild)
          )
        )
      )
    )
  }

  private def divSidebar(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "sidebar")(
      section(
        className := optionalClasses(
          Seq(
            ("node-icon", true),
            ("empty", !node.hasIcon)
          )
        )
      )(
        if (node.hasIcon)
          Fragment(
            PartsNode.imgNode(node),
            Option.when(model.isSelf) {
              buttonEdit(_ => dispatch(Modal.Msg.OpenModal(Modal.NodeIcon())))
            }
          )
        else
          span(className := "empty-icon")(
            context.i18n.text.Node_notYetConnected
          )
      ),
      model.nodeRoleName.map { role =>
        Fragment(
          section(className := "node-role")(role),
          Option.when(!model.isSelf) {
            sectionNodeRelationship(
              context.repo.nodes.childPrivilegesTo(node.id)
            )
          }
        )
      }
    )

  private def sectionNodeRelationship(privileges: Option[ChildNode])(implicit
      context: Context
  ): ReactElement =
    section(className := "node-relationship")(
      div(className := "arrow")(materialSymbol("arrow_upward")),
      privileges.map { privileges =>
        ul(className := "privileges")(
          PartsNode.childPrivileges(privileges).map(li()(_)): _*
        )
      },
      section(className := "self-node")(
        context.repo.nodes.self.map(PartsNode.imgNode(_))
      )
    )

  private def sectionToolButtons(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "tool-buttons")(
      // Operate as
      PartsNode.buttonOperateAs(node, "left")
    )

  private def fieldId(node: Node)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.Id,
      classes = "node-id",
      inputValue = node.id.uuid,
      readOnly = true
    )

  private def fieldName(node: Node, rootCoto: Option[Coto], model: Model)(
      implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.Name,
      classes = "node-name"
    )(
      input(
        `type` := "text",
        name := "nodeName",
        readOnly := true,
        value := node.name
      ),
      Option.when(model.isSelf) {
        div(className := "edit")(
          rootCoto.map(buttonEditRootCoto)
        )
      }
    )

  private def fieldDescription(rootCoto: Coto, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = "Description",
      classes = "node-description"
    )(
      section(className := "node-description")(
        PartsCoto.sectionCotonomaContent(rootCoto)
      ),
      Option.when(model.isSelf) {
        div(className := "edit")(
          buttonEditRootCoto(rootCoto)
        )
      }
    )

  private def fieldOwnerPassword(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.ModalNodeProfile_ownerPassword,
      classes = "owner-password"
    )(
      button(
        `type` := "button",
        className := "reset-password contrast outline",
        disabled := model.resettingPassword,
        aria - "busy" := model.resettingPassword.toString(),
        onClick := (_ =>
          dispatch(
            Modal.Msg.OpenModal(
              Modal.Confirm(
                context.i18n.text.Owner_confirmResetPassword,
                Msg.ResetOwnerPassword
              )
            )
          )
        )
      )(context.i18n.text.Owner_resetPassword),
      model.resettingPasswordError.map(
        section(className := "error")(_)
      )
    )

  private def buttonEditRootCoto(rootCoto: Coto)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    buttonEdit(_ =>
      dispatch(
        (Modal.Msg.OpenModal.apply _).tupled(
          Modal.EditCoto(rootCoto)
        )
      )
    )
}
