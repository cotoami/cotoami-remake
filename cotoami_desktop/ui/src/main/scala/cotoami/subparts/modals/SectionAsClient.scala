package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ActiveClient, Client, ClientNode, Id, Node}
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.subparts.{field, fieldInput, Modal}

object SectionAsClient {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      loading: Boolean = false,
      client: Option[Client] = None,
      resettingPassword: Boolean = false,
      resettingPasswordError: Option[String] = None
  )

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (!context.repo.nodes.isOperating(nodeId))
        (
          Model(nodeId, loading = true),
          ClientNodeBackend.fetch(nodeId)
            .map(Msg.ClientNodeFetched(_).into)
        )
      else
        (Model(nodeId), Cmd.none)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.SectionAsClientMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ClientNodeFetched(result: Either[ErrorJson, ClientNode])
        extends Msg
    case class ResetClientPassword(node: Node) extends Msg
    case class ClientPasswordReset(
        node: Node,
        result: Either[ErrorJson, String]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.ClientNodeFetched(Right(clientNode)) =>
        (
          model.copy(
            client = context.repo.nodes.clientInfo(clientNode),
            loading = false
          ),
          Cmd.none
        )

      case Msg.ClientNodeFetched(Left(_)) =>
        (model.copy(loading = false), Cmd.none)

      case Msg.ResetClientPassword(node) =>
        (
          model.copy(resettingPassword = true),
          ClientNodeBackend.resetPassword(node.id)
            .map(Msg.ClientPasswordReset(node, _).into)
        )

      case Msg.ClientPasswordReset(node, Right(password)) =>
        (
          model.copy(resettingPassword = false),
          Modal.open(Modal.NewPassword.forClient(node, password))
        )

      case Msg.ClientPasswordReset(node, Left(e)) =>
        (
          model.copy(
            resettingPassword = false,
            resettingPasswordError = Some(e.default_message)
          ),
          cotoami.error("Couldn't reset the client password.", e)
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    model.client.map { client =>
      section(className := "field-group pas-client")(
        h2()(context.i18n.text.AsClient_title),
        fieldPassword(client, model),
        fieldLastLogin(client),
        client.active.map(fieldRemoteAddress)
      )
    }.getOrElse(
      Option.when(model.loading) {
        section(
          className := "field-group as-client",
          aria - "busy" := model.loading.toString()
        )()
      }
    )

  private def fieldPassword(client: Client, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.AsClient_password,
      classes = "password"
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
                context.i18n.text.AsClient_confirmResetPassword,
                Msg.ResetClientPassword(client.node)
              )
            )
          )
        )
      )(context.i18n.text.AsClient_resetPassword)
    )

  private def fieldLastLogin(client: Client)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.AsClient_lastLogin,
      classes = "last-login",
      inputValue = client.client.lastSessionCreatedAt
        .map(context.time.formatDateTime)
        .getOrElse("-"): String,
      readOnly = true
    )

  private def fieldRemoteAddress(active: ActiveClient)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.AsClient_remoteAddress,
      classes = "remote-address",
      inputValue = active.remoteAddr,
      readOnly = true
    )
}
