package cotoami.subparts.modeless

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.{materialSymbol, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Coto, Id, Node, Server}
import cotoami.repository.{Nodes, Root}
import cotoami.subparts.forms.{buttonEdit, field, fieldInput}
import cotoami.subparts.{PartsCoto, PartsNode}

object ModelessNodeProfile {

  val DialogId = ModelessDialogId.NodeProfile

  case class Model(
      nodeId: Id[Node],
      error: Option[String] = None,
      imageMaxSize: FieldImageMaxSize.Model,
      ownerPassword: FieldOwnerPassword.Model,
      selfNodeServer: SectionSelfNodeServer.Model,
      asClient: SectionAsClient.Model,
      asChild: SectionAsChild.Model
  ) {
    def isLocal(using context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isSelf(using context: Context): Boolean =
      context.repo.nodes.isSelf(nodeId)

    def asServer(using context: Context): Option[Server] =
      context.repo.nodes.servers.get(nodeId)

    def nodeRoleName(using context: Context): Option[String] =
      if (isSelf)
        Some(
          context.i18n.text.ModelessNodeProfile_selfNode ++
            Option.when(!isLocal)(
              s" (${context.i18n.text.ModelessNodeProfile_switched})"
            ).getOrElse("")
        )
      else if (asServer.isDefined)
        Some(context.i18n.text.Server)
      else if (asClient.client.isDefined)
        Some(context.i18n.text.Client)
      else
        None
  }

  object Model {
    def apply(nodeId: Id[Node])(using context: Context): (Model, Cmd[AppMsg]) = {
      val (selfNodeServer, selfNodeServerCmd) =
        SectionSelfNodeServer.Model(nodeId)
      val (asClient, asClientCmd) = SectionAsClient.Model(nodeId)
      val (asChild, asChildCmd) = SectionAsChild.Model(nodeId)
      (
        Model(
          nodeId = nodeId,
          imageMaxSize = FieldImageMaxSize.Model(nodeId),
          ownerPassword = FieldOwnerPassword.Model(nodeId),
          selfNodeServer = selfNodeServer,
          asClient = asClient,
          asChild = asChild
        ),
        Root.fetchNodeDetails(nodeId) ++
          selfNodeServerCmd ++
          asClientCmd ++
          asChildCmd
      )
    }
  }

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessNodeProfileMsg(this)
  }

  object Msg {
    case class Open(nodeId: Id[Node]) extends Msg
    case object Focus extends Msg
    case object Close extends Msg
    case class FieldImageMaxSizeMsg(submsg: FieldImageMaxSize.Msg) extends Msg
    case class FieldOwnerPasswordMsg(submsg: FieldOwnerPassword.Msg) extends Msg
    case class SectionSelfNodeServerMsg(submsg: SectionSelfNodeServer.Msg)
        extends Msg
    case class SectionAsClientMsg(submsg: SectionAsClient.Msg) extends Msg
    case class SectionAsChildMsg(submsg: SectionAsChild.Msg) extends Msg
  }

  def dialogOrderAction(msg: Msg): Option[ModelessDialogOrder.Action] =
    msg match {
      case Msg.Open(_) => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Focus   => Some(ModelessDialogOrder.Action.Focus)
      case Msg.Close   => Some(ModelessDialogOrder.Action.Close)
      case _           => None
    }

  def open(nodeId: Id[Node]): Cmd.One[AppMsg] =
    Browser.send(Msg.Open(nodeId).into)

  def close: Cmd.One[AppMsg] =
    Browser.send(Msg.Close.into)

  def update(msg: Msg, model: Option[Model])(using
      context: Context
  ): (Option[Model], Nodes, Cmd[AppMsg]) = {
    val default = (model, context.repo.nodes, Cmd.none)

    (msg, model) match {
      case (Msg.Open(nodeId), _) =>
        Model(nodeId).pipe { case (opened, cmd) =>
          (Some(opened), context.repo.nodes, cmd)
        }

      case (Msg.Focus, _) =>
        default

      case (Msg.Close, _) =>
        default.copy(_1 = None)

      case (_, None) =>
        default

      case (Msg.FieldImageMaxSizeMsg(submsg), Some(current)) =>
        val (imageMaxSize, nodes, cmd) =
          FieldImageMaxSize.update(submsg, current.imageMaxSize)
        (
          Some(current.copy(imageMaxSize = imageMaxSize)),
          nodes,
          cmd
        )

      case (Msg.FieldOwnerPasswordMsg(submsg), Some(current)) =>
        val (ownerPassword, cmd) =
          FieldOwnerPassword.update(submsg, current.ownerPassword)
        (
          Some(current.copy(ownerPassword = ownerPassword)),
          context.repo.nodes,
          cmd
        )

      case (Msg.SectionSelfNodeServerMsg(submsg), Some(current)) =>
        val (selfNodeServer, nodes, cmd) =
          SectionSelfNodeServer.update(submsg, current.selfNodeServer)
        (
          Some(current.copy(selfNodeServer = selfNodeServer)),
          nodes,
          cmd
        )

      case (Msg.SectionAsClientMsg(submsg), Some(current)) =>
        val (asClient, cmd) = SectionAsClient.update(submsg, current.asClient)
        (
          Some(current.copy(asClient = asClient)),
          context.repo.nodes,
          cmd
        )

      case (Msg.SectionAsChildMsg(submsg), Some(current)) =>
        val (asChild, cmd) = SectionAsChild.update(submsg, current.asChild)
        (
          Some(current.copy(asChild = asChild)),
          context.repo.nodes,
          cmd
        )
    }
  }

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    ModelessDialogFrame(
      dialogClasses = Seq("modeless-node-profile" -> true),
      title = Fragment(
        span(className := "title-icon")(materialSymbol(Node.IconName)),
        context.i18n.text.ModelessNodeProfile_title
      ),
      onClose = () => dispatch(Msg.Close),
      onFocus = () => dispatch(Msg.Focus),
      zIndex = context.modeless.dialogZIndex(DialogId),
      resizable = false,
      lockMeasuredSize = false,
      initialWidth = "600px",
      initialHeight = "auto",
      error = model.error
    )(
      context.repo.nodes.get(model.nodeId)
        .map(modelessContent(_, model))
        .getOrElse(s"Node ${model.nodeId} not found.")
    )

  private def modelessContent(node: Node, model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val rootCoto = context.repo.rootOf(model.nodeId).map(_._2)
    Fragment(
      divSidebar(node, model),
      div(className := "main")(
        sectionToolButtons(node),
        div(className := "fields")(
          ScrollArea(className = Some("scroll-fields"))(
            fieldId(node),
            fieldName(node, rootCoto, model),
            rootCoto.map(fieldDescription(_, model)),
            FieldImageMaxSize(model.imageMaxSize),
            FieldOwnerPassword(model.ownerPassword),
            SectionSelfNodeServer(model.selfNodeServer),
            model.asServer.map(SectionAsServer(_)),
            SectionAsClient(model.asClient),
            SectionAsChild(model.asChild)
          )
        )
      )
    )
  }

  private def divSidebar(node: Node, model: Model)(using
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
              buttonEdit(_ => dispatch(cotoami.subparts.Modal.Msg.OpenModal(cotoami.subparts.Modal.NodeIcon())))
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
  )(using
      context: Context
  ): ReactElement =
    section(className := "node-relationship")(
      div(className := "arrow")(materialSymbol("arrow_upward")),
      Option.when(isParent) {
        ul(className := "privileges")(
          PartsNode.childPrivileges(privileges).map(li()(_))*
        )
      },
      section(className := "self-node")(
        context.repo.nodes.self.map(PartsNode.imgNode(_))
      )
    )

  private def sectionToolButtons(node: Node)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "tool-buttons")(
      PartsNode.buttonSwitchNode(node, "left")
    )

  private def fieldId(node: Node)(using
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.Id,
      classes = "node-id",
      inputValue = node.id.uuid,
      readOnly = true
    )

  private def fieldName(node: Node, rootCoto: Option[Coto], model: Model)(using
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

  private def fieldDescription(rootCoto: Coto, model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.ModelessNodeProfile_description,
      classes = "node-description"
    )(
      section(className := "node-description")(
        PartsCoto.sectionCotonomaContent(rootCoto)
      ),
      divEditRootCoto(rootCoto, model)
    )

  private def divEditRootCoto(rootCoto: Coto, model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(model.isSelf) {
      div(className := "edit")(
        buttonEdit(
          _ => dispatch(ModelessEditCoto.Msg.Open(rootCoto)),
          disabled = context.modeless.isOpen(ModelessEditCoto.DialogId)
        )
      )
    }
}
