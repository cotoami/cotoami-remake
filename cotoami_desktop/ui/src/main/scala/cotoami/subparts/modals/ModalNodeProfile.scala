package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ChildNode, ClientNode, Coto, Id, Node, Page, Server}
import cotoami.repository.{Nodes, Root}
import cotoami.backend.{
  ClientNodeBackend,
  Commands,
  DatabaseInfo,
  ErrorJson,
  LocalServer,
  ServerConfig
}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  toolButton,
  ScrollArea
}
import cotoami.subparts.{
  labeledField,
  labeledInputField,
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
      generatingOwnerPassword: Boolean = false,
      clientCount: Double = 0,
      localServer: Option[LocalServer] = None,
      enablingAnonymousRead: Boolean = false,
      error: Option[String] = None
  ) {
    def isLocalNode()(implicit context: Context): Boolean =
      context.repo.nodes.isLocal(nodeId)

    def isOperatingNode()(implicit context: Context): Boolean =
      context.repo.nodes.isOperating(nodeId)

    def anonymousReadEnabled: Boolean =
      localServer.map(_.anonymousReadEnabled).getOrElse(false)
  }

  object Model {
    def apply(nodeId: Id[Node], nodes: Nodes): (Model, Cmd[AppMsg]) =
      (
        Model(nodeId),
        Cmd.Batch(
          Root.fetchNodeDetails(nodeId),
          fetchClientCount,
          if (nodes.isOperating(nodeId))
            LocalServer.fetch.map(Msg.LocalServerFetched(_).into)
          else
            Cmd.none
        )
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NodeProfileMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object GenerateOwnerPassword extends Msg
    case class OwnerPasswordGenerated(result: Either[ErrorJson, String])
        extends Msg
    case class LocalServerFetched(result: Either[ErrorJson, LocalServer])
        extends Msg
    case class ClientCountFetched(result: Either[ErrorJson, Page[ClientNode]])
        extends Msg
    case class EnableAnonymousRead(enable: Boolean) extends Msg
    case class AnonymousReadEnabled(result: Either[ErrorJson, Boolean])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.GenerateOwnerPassword =>
        (
          model.copy(generatingOwnerPassword = true),
          DatabaseInfo.newOwnerPassword
            .map(Msg.OwnerPasswordGenerated(_).into)
        )

      case Msg.OwnerPasswordGenerated(Right(password)) =>
        (
          model.copy(generatingOwnerPassword = false),
          Modal.open(Modal.NewPassword(password))
        )

      case Msg.OwnerPasswordGenerated(Left(e)) =>
        (
          model.copy(
            generatingOwnerPassword = false,
            error = Some(e.default_message)
          ),
          cotoami.error("Couldn't generate an owner password.", e)
        )

      case Msg.LocalServerFetched(Right(server)) =>
        (model.copy(localServer = Some(server)), Cmd.none)

      case Msg.LocalServerFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch the local server.", e))

      case Msg.ClientCountFetched(Right(page)) =>
        (model.copy(clientCount = page.totalItems), Cmd.none)

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
    val asServer = context.repo.nodes.servers.get(model.nodeId)
    Fragment(
      divSidebar(node, model),
      div(className := "main")(
        divTools(node, model),
        div(className := "fields")(
          ScrollArea(className = Some("scroll-fields"))(
            fieldId(node),
            fieldName(node, rootCoto, model),
            asServer.map(fieldServerUrl),
            rootCoto.map(fieldDescription(_, model)),
            model.localServer.flatMap(_.activeConfig).map(
              sectionLocalServer(_, model)
            )
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
            Option.when(model.isOperatingNode()) {
              buttonEdit(_ => dispatch(Modal.Msg.OpenModal(Modal.NodeIcon())))
            }
          )
        else
          span(className := "empty-icon")(
            context.i18n.text.Node_notYetConnected
          )
      ),
      if (context.repo.nodes.isOperating(node.id))
        section(className := "operating-node-mark")(
          "You",
          Option.when(!context.repo.nodes.isLocal(node.id)) {
            " (switched)"
          }
        )
      else
        context.repo.nodes.childPrivilegesTo(node.id)
          .map(sectionOperatingNodeAsChild)
    )

  private def sectionOperatingNodeAsChild(privileges: ChildNode)(implicit
      context: Context
  ): ReactElement =
    section(className := "operating-node")(
      div(className := "arrow")(materialSymbol("arrow_upward")),
      section(className := "privileges")(
        if (privileges.asOwner)
          context.i18n.text.Owner
        else if (privileges.canEditItos)
          s"${context.i18n.text.Post}, ${context.i18n.text.EditItos}"
        else
          context.i18n.text.Post
      ),
      context.repo.nodes.operating.map(PartsNode.imgNode(_))
    )

  private def divTools(node: Node, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    div(className := "tools")(
      PartsNode.buttonOperateAs(node, "left"),
      Option.when(model.isLocalNode() && model.isOperatingNode()) {
        div(className := "generate-owner-password")(
          span(
            className := "processing",
            aria - "busy" := model.generatingOwnerPassword.toString()
          )(),
          toolButton(
            classes = "generate-owner-password",
            symbol = "key",
            tip =
              Some(context.i18n.text.ModalNodeProfile_generateOwnerPassword),
            tipPlacement = "left",
            disabled = model.generatingOwnerPassword,
            onClick = e =>
              dispatch(
                Modal.Msg.OpenModal(
                  Modal.Confirm(
                    context.i18n.text.ModalNodeProfile_confirmGenerateOwnerPassword,
                    Msg.GenerateOwnerPassword
                  )
                )
              )
          )
        )
      }
    )

  private def fieldId(node: Node): ReactElement =
    labeledInputField(
      classes = "node-id",
      label = "ID",
      inputId = "node-profile-id",
      inputType = "text",
      inputValue = node.id.uuid,
      readOnly = true
    )

  private def fieldName(node: Node, rootCoto: Option[Coto], model: Model)(
      implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "node-name",
      label = "Name",
      labelFor = Some("node-profile-name")
    )(
      div(className := "input-with-tools")(
        input(
          `type` := "text",
          id := "node-profile-name",
          name := "nodeName",
          readOnly := true,
          value := node.name
        ),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            rootCoto.map(buttonEditRootCoto)
          )
        }
      )
    )

  private def fieldServerUrl(server: Server): ReactElement =
    labeledField(
      classes = "server-url",
      label = "Server URL",
      labelFor = Some("node-profile-server-url")
    )(
      div(className := "input-with-tools")(
        input(
          `type` := "text",
          id := "node-profile-server-url",
          readOnly := true,
          value := server.server.urlPrefix
        ),
        div(className := "tools")(
          // buttonEdit(_ => ())
        )
      )
    )

  private def fieldDescription(rootCoto: Coto, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "node-description",
      label = "Description",
      labelFor = Some("node-profile-description")
    )(
      div(className := "input-with-tools")(
        section(className := "node-description")(
          PartsCoto.sectionCotonomaContent(rootCoto)
        ),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            buttonEditRootCoto(rootCoto)
          )
        }
      )
    )

  private def sectionLocalServer(
      config: ServerConfig,
      model: Model
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "local-server")(
      h2()("Local server"),
      fieldLocalServerUrl(config),
      fieldClientNodes(model),
      fieldAnonymousRead(model)
    )

  private def fieldLocalServerUrl(config: ServerConfig): ReactElement =
    labeledInputField(
      classes = "local-server-url",
      label = "Local server URL",
      inputId = "node-profile-local-server-url",
      inputType = "text",
      inputValue = config.url,
      readOnly = true
    )

  private def fieldClientNodes(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    labeledField(
      classes = "client-nodes",
      label = "Client nodes",
      labelFor = Some("node-profile-client-nodes")
    )(
      div(className := "input-with-tools")(
        sectionClientNodesCount(model.clientCount, context.repo.nodes),
        Option.when(model.isOperatingNode()) {
          div(className := "tools")(
            buttonEdit(_ =>
              dispatch(
                (Modal.Msg.OpenModal.apply _).tupled(
                  Modal.Clients()
                )
              )
            )
          )
        }
      )
    )

  private def fieldAnonymousRead(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    labeledField(
      classes = "anonymous-read",
      label = "Anonymous read",
      labelFor = Some("node-profile-anonymous-read")
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
                  "Are you sure you want to allow anonymous read-only access?" ++
                    " (Anyone who knows this node's URL can view your cotos and itos.)",
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
