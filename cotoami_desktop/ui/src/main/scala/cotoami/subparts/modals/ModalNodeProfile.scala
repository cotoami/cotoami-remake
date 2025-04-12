package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{materialSymbol, toolButton, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, Client, ClientNode, Coto, Id, Node, Server}
import cotoami.repository.Root
import cotoami.backend.{
  ChildNodeBackend,
  ClientNodeBackend,
  Commands,
  DatabaseInfo,
  ErrorJson,
  LocalServer,
  ServerConfig
}
import cotoami.subparts.{
  field,
  fieldInput,
  sectionClientNodesCount,
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
      generatingPassword: Boolean = false,

      // For client node
      client: Option[Client] = None,

      // For child node
      child: Option[ChildNode] = None,
      asOwner: Boolean = false,
      canEditItos: Boolean = false,

      // For local server
      clientCount: Double = 0,
      localServer: Option[LocalServer] = None,
      enablingAnonymousRead: Boolean = false
  ) {
    def isLocalNode()(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isOperatedNode()(implicit context: Context): Boolean =
      context.repo.nodes.isOperating(nodeId)

    def isChild: Boolean = child.isDefined

    def setChild(child: ChildNode): Model =
      copy(
        child = Some(child),
        asOwner = child.asOwner,
        canEditItos = child.canEditItos
      )

    def anonymousReadEnabled: Boolean =
      localServer.map(_.anonymousReadEnabled).getOrElse(false)
  }

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      (
        Model(nodeId),
        Root.fetchNodeDetails(nodeId) ++
          (if (context.repo.nodes.isOperating(nodeId))
             Cmd.Batch(
               fetchClientCount,
               LocalServer.fetch.map(Msg.LocalServerFetched(_).into)
             )
           else
             Cmd.Batch(
               ClientNodeBackend.fetch(nodeId)
                 .map(Msg.ClientNodeFetched(_).into),
               ChildNodeBackend.fetch(nodeId)
                 .map(Msg.ChildNodeFetched(_).into)
             ))
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    // For local node
    case object GenerateOwnerPassword extends Msg
    case class OwnerPasswordGenerated(result: Either[ErrorJson, String])
        extends Msg

    // For client node
    case class ClientNodeFetched(result: Either[ErrorJson, ClientNode])
        extends Msg
    case class GenerateClientPassword(node: Node) extends Msg
    case class ClientPasswordGenerated(
        node: Node,
        result: Either[ErrorJson, String]
    ) extends Msg

    // For child node
    case class ChildNodeFetched(result: Either[ErrorJson, ChildNode])
        extends Msg

    // For local server
    case class LocalServerFetched(result: Either[ErrorJson, LocalServer])
        extends Msg
    case class ClientCountFetched(result: Either[ErrorJson, Double]) extends Msg
    case class EnableAnonymousRead(enable: Boolean) extends Msg
    case class AnonymousReadEnabled(result: Either[ErrorJson, Boolean])
        extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.GenerateOwnerPassword =>
        (
          model.copy(generatingPassword = true),
          DatabaseInfo.newOwnerPassword
            .map(Msg.OwnerPasswordGenerated(_).into)
        )

      case Msg.OwnerPasswordGenerated(Right(password)) =>
        (
          model.copy(generatingPassword = false),
          Modal.open(Modal.NewPassword.forOwner(password))
        )

      case Msg.OwnerPasswordGenerated(Left(e)) =>
        (
          model.copy(
            generatingPassword = false,
            error = Some(e.default_message)
          ),
          cotoami.error("Couldn't generate an owner password.", e)
        )

      case Msg.ClientNodeFetched(result) =>
        result match {
          case Right(clientNode) =>
            (
              model.copy(client = context.repo.nodes.clientInfo(clientNode)),
              Cmd.none
            )
          case Left(_) => (model, Cmd.none)
        }

      case Msg.GenerateClientPassword(node) =>
        (
          model.copy(generatingPassword = true),
          ClientNodeBackend.generatePassword(node.id)
            .map(Msg.ClientPasswordGenerated(node, _).into)
        )

      case Msg.ClientPasswordGenerated(node, Right(password)) =>
        (
          model.copy(generatingPassword = false),
          Modal.open(Modal.NewPassword.forClient(node, password))
        )

      case Msg.ClientPasswordGenerated(node, Left(e)) =>
        (
          model.copy(
            generatingPassword = false,
            error = Some(e.default_message)
          ),
          cotoami.error("Couldn't generate a client password.", e)
        )

      case Msg.ChildNodeFetched(result) =>
        result match {
          case Right(child) => (model.setChild(child), Cmd.none)
          case Left(_)      => (model, Cmd.none)
        }

      case Msg.LocalServerFetched(Right(server)) =>
        (model.copy(localServer = Some(server)), Cmd.none)

      case Msg.LocalServerFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch the local server.", e))

      case Msg.ClientCountFetched(Right(count)) =>
        (model.copy(clientCount = count), Cmd.none)

      case Msg.ClientCountFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch client count.", e))

      case Msg.EnableAnonymousRead(enable) =>
        (
          model.copy(enablingAnonymousRead = true),
          enableAnonymousRead(enable).map(Msg.AnonymousReadEnabled(_).into)
        )

      case Msg.AnonymousReadEnabled(Right(enabled)) =>
        (
          model
            .modify(_.enablingAnonymousRead).setTo(false)
            .modify(_.localServer.each.anonymousReadEnabled).setTo(enabled)
            .modify(_.localServer.each.anonymousConnections).setTo(0),
          Cmd.none
        )

      case Msg.AnonymousReadEnabled(Left(e)) =>
        (model, cotoami.error("Couldn't enable/disable anonymous read.", e))
    }

  def fetchClientCount: Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(0, Some(1))
      .map(_.map(_.totalItems))
      .map(Msg.ClientCountFetched(_).into)

  def enableAnonymousRead(
      enable: Boolean
  ): Cmd.One[Either[ErrorJson, Boolean]] =
    Commands.send(Commands.EnableAnonymousRead(enable))

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
            context.repo.nodes.servers.get(model.nodeId).map(fieldUrl),
            rootCoto.map(fieldDescription(_, model)),
            model.localServer.flatMap(_.activeConfig)
              .map(sectionLocalServer(_, model)),
            model.client.map(fieldsClient),
            fieldChildPrivileges(model)
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
            Option.when(model.isOperatedNode()) {
              buttonEdit(_ => dispatch(Modal.Msg.OpenModal(Modal.NodeIcon())))
            }
          )
        else
          span(className := "empty-icon")(
            context.i18n.text.Node_notYetConnected
          )
      ),
      if (context.repo.nodes.isOperating(node.id))
        section(className := "operated-node-mark")(
          "You",
          Option.when(!context.repo.nodes.isLocal(node.id)) {
            " (switched)"
          }
        )
      else
        model.client
          .map(_ => sectionOperatedNodeAsServer)
          .getOrElse(
            context.repo.nodes.childPrivilegesTo(node.id)
              .map(sectionOperatedNodeAsChild)
          )
    )

  private def sectionOperatedNodeAsChild(privileges: ChildNode)(implicit
      context: Context
  ): ReactElement =
    section(className := "operated-node")(
      div(className := "arrow")(materialSymbol("arrow_upward")),
      section(className := "privileges")(
        if (privileges.asOwner)
          context.i18n.text.Owner
        else if (privileges.canEditItos)
          s"${context.i18n.text.Post}, ${context.i18n.text.EditItos}"
        else
          context.i18n.text.Post
      ),
      context.repo.nodes.operated.map(PartsNode.imgNode(_))
    )

  private def sectionOperatedNodeAsServer(implicit
      context: Context
  ): ReactElement =
    section(className := "operated-node")(
      div(className := "arrow")(materialSymbol("arrow_upward")),
      section(className := "privileges")("Server"),
      context.repo.nodes.operated.map(PartsNode.imgNode(_))
    )

  private def sectionToolButtons(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "tool-buttons")(
      // Operate as
      PartsNode.buttonOperateAs(node, "left"),

      // Generate Owner Password
      Option.when(model.isLocalNode() && model.isOperatedNode()) {
        buttonGeneratePassword(
          context.i18n.text.ModalNodeProfile_generateOwnerPassword,
          model,
          e =>
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  context.i18n.text.ModalNodeProfile_confirmGenerateOwnerPassword,
                  Msg.GenerateOwnerPassword
                )
              )
            )
        )
      },

      // Generate Client Password
      model.client.flatMap { client =>
        Option.when(!model.isLocalNode()) {
          buttonGeneratePassword(
            context.i18n.text.ModalNodeProfile_generateClientPassword,
            model,
            e =>
              dispatch(
                Modal.Msg.OpenModal(
                  Modal.Confirm(
                    context.i18n.text.ModalNodeProfile_confirmGenerateClientPassword,
                    Msg.GenerateClientPassword(client.node)
                  )
                )
              )
          )
        }
      }
    )

  private def buttonGeneratePassword(
      tip: String,
      model: Model,
      onClick: SyntheticMouseEvent[_] => Unit
  ): ReactElement =
    div(className := "generate-password")(
      span(
        className := "processing",
        aria - "busy" := model.generatingPassword.toString()
      )(),
      toolButton(
        classes = "generate-password",
        symbol = "key",
        tip = Some(tip),
        tipPlacement = "left",
        disabled = model.generatingPassword,
        onClick = onClick
      )
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
      name = "Name",
      classes = "node-name"
    )(
      input(
        `type` := "text",
        name := "nodeName",
        readOnly := true,
        value := node.name
      ),
      Option.when(model.isOperatedNode()) {
        div(className := "edit")(
          rootCoto.map(buttonEditRootCoto)
        )
      }
    )

  private def fieldUrl(server: Server): ReactElement =
    field(
      name = "URL",
      classes = "server"
    )(
      input(
        `type` := "text",
        readOnly := true,
        value := server.server.urlPrefix
      ),
      div(className := "edit")(
        // buttonEdit(_ => ())
      )
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
      Option.when(model.isOperatedNode()) {
        div(className := "edit")(
          buttonEditRootCoto(rootCoto)
        )
      }
    )

  private def fieldsClient(client: Client)(implicit
      context: Context
  ): ReactElement =
    Fragment(
      fieldInput(
        name = context.i18n.text.ModalNodeProfile_clientLastLogin,
        classes = "client-last-login",
        inputValue = client.client.lastSessionCreatedAt
          .map(context.time.formatDateTime)
          .getOrElse("-"): String,
        readOnly = true
      ),
      client.active.map(active =>
        fieldInput(
          name = context.i18n.text.ModalNodeProfile_clientRemoteAddress,
          classes = "client-remote-address",
          inputValue = active.remoteAddr,
          readOnly = true
        )
      )
    )

  private def fieldChildPrivileges(model: Model)(implicit
      context: Context
  ): Option[ReactElement] =
    Option.when(model.isChild) {
      field(
        name = context.i18n.text.ChildPrivileges,
        classes = "privileges"
      )(
        PartsNode.inputChildPrivileges(
          asOwner = model.asOwner,
          canEditItos = model.canEditItos,
          disabled = true,
          onAsOwnerChange = (_ => ()),
          onCanEditItosChange = (_ => ())
        ),
        div(className := "edit")(
          buttonEdit(_ => ())
        )
      )
    }

  private def sectionLocalServer(
      config: ServerConfig,
      model: Model
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "local-server")(
      h2()(context.i18n.text.ModalNodeProfile_localServer),
      fieldLocalServerUrl(config),
      fieldClientNodes(model),
      fieldAnonymousRead(model)
    )

  private def fieldLocalServerUrl(config: ServerConfig)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.ModalNodeProfile_localServerUrl,
      classes = "local-server-url",
      inputValue = config.url,
      readOnly = true
    )

  private def fieldClientNodes(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.ModalNodeProfile_clientNodes,
      classes = "client-nodes"
    )(
      sectionClientNodesCount(model.clientCount, context.repo.nodes),
      Option.when(model.isOperatedNode()) {
        div(className := "edit")(
          buttonEdit(_ =>
            dispatch((Modal.Msg.OpenModal.apply _).tupled(Modal.Clients()))
          )
        )
      }
    )

  private def fieldAnonymousRead(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    field(
      name = context.i18n.text.ModalNodeProfile_anonymousRead,
      classes = "anonymous-read"
    )(
      input(
        `type` := "checkbox",
        role := "switch",
        checked := model.anonymousReadEnabled,
        disabled := model.enablingAnonymousRead,
        onChange := (_ =>
          if (model.anonymousReadEnabled)
            dispatch(Msg.EnableAnonymousRead(false)) // disable
          else
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  context.i18n.text.ModalNodeProfile_confirmEnableAnonymousRead,
                  Msg.EnableAnonymousRead(true) // enable
                )
              )
            )
        )
      ),
      Option.when(model.enablingAnonymousRead) {
        span(className := "processing", aria - "busy" := "true")()
      },
      Option.when(model.anonymousReadEnabled) {
        model.localServer.map(_.anonymousConnections).map(count =>
          span(className := "anonymous-connections")(
            s"(Active connections: ${count})"
          )
        )
      }
    )

  private def buttonEditRootCoto(rootCoto: Coto)(implicit
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    buttonEdit(_ =>
      dispatch(
        (Modal.Msg.OpenModal.apply _).tupled(
          Modal.EditCoto(rootCoto)
        )
      )
    )

  private def buttonEdit(
      onClick: SyntheticMouseEvent[_] => Unit
  ): ReactElement =
    toolButton(
      symbol = "edit",
      tip = Some("Edit"),
      classes = "edit",
      onClick = onClick
    )
}
