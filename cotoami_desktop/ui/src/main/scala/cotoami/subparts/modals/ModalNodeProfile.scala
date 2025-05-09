package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Coto, Id, Node, Server}
import cotoami.repository.{Nodes, Root}
import cotoami.subparts.{Modal, PartsCoto, PartsNode}

object ModalNodeProfile {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      error: Option[String] = None,

      // Fields
      imageMaxSize: FieldImageMaxSize.Model,
      ownerPassword: FieldOwnerPassword.Model,
      selfServer: SectionSelfServer.Model,
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
      val (selfServer, selfServerCmd) = SectionSelfServer.Model(nodeId)
      val (asClient, asClientCmd) = SectionAsClient.Model(nodeId)
      val (asChild, asChildCmd) = SectionAsChild.Model(nodeId)
      (
        Model(
          nodeId = nodeId,
          imageMaxSize = FieldImageMaxSize.Model(nodeId),
          ownerPassword = FieldOwnerPassword.Model(nodeId),
          selfServer = selfServer,
          asClient = asClient,
          asChild = asChild
        ),
        Root.fetchNodeDetails(nodeId) ++
          selfServerCmd ++
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
    case class FieldImageMaxSizeMsg(submsg: FieldImageMaxSize.Msg) extends Msg
    case class FieldOwnerPasswordMsg(submsg: FieldOwnerPassword.Msg) extends Msg
    case class SectionSelfServerMsg(submsg: SectionSelfServer.Msg) extends Msg
    case class SectionAsClientMsg(submsg: SectionAsClient.Msg) extends Msg
    case class SectionAsChildMsg(submsg: SectionAsChild.Msg) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) =
    msg match {
      case Msg.FieldImageMaxSizeMsg(submsg) => {
        val (imageMaxSize, nodes, cmd) =
          FieldImageMaxSize.update(submsg, model.imageMaxSize)
        (model.copy(imageMaxSize = imageMaxSize), nodes, cmd)
      }

      case Msg.FieldOwnerPasswordMsg(submsg) => {
        val (ownerPassword, cmd) =
          FieldOwnerPassword.update(submsg, model.ownerPassword)
        (model.copy(ownerPassword = ownerPassword), context.repo.nodes, cmd)
      }

      case Msg.SectionSelfServerMsg(submsg) => {
        val (selfServer, nodes, cmd) =
          SectionSelfServer.update(submsg, model.selfServer)
        (model.copy(selfServer = selfServer), nodes, cmd)
      }

      case Msg.SectionAsClientMsg(submsg) => {
        val (asClient, cmd) = SectionAsClient.update(submsg, model.asClient)
        (model.copy(asClient = asClient), context.repo.nodes, cmd)
      }

      case Msg.SectionAsChildMsg(submsg) => {
        val (asChild, cmd) = SectionAsChild.update(submsg, model.asChild)
        (model.copy(asChild = asChild), context.repo.nodes, cmd)
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
            FieldImageMaxSize(model.imageMaxSize),
            FieldOwnerPassword(model.ownerPassword),
            SectionSelfServer(model.selfServer),
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
              context.repo.nodes.isParent(node.id),
              context.repo.nodes.childPrivilegesTo(node.id)
            )
          }
        )
      }
    )

  private def sectionNodeRelationship(
      isParent: Boolean,
      privileges: Option[ChildNode]
  )(implicit
      context: Context
  ): ReactElement =
    section(className := "node-relationship")(
      div(className := "arrow")(materialSymbol("arrow_upward")),
      Option.when(isParent) {
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
      PartsNode.buttonSwitchNode(node, "left")
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
      rootCoto.map(divEditRootCoto(_, model))
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
      divEditRootCoto(rootCoto, model)
    )

  private def divEditRootCoto(rootCoto: Coto, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(model.isSelf) {
      div(className := "edit")(
        buttonEdit(_ =>
          dispatch(
            (Modal.Msg.OpenModal.apply _).tupled(
              Modal.EditCoto(rootCoto)
            )
          )
        )
      )
    }
}
