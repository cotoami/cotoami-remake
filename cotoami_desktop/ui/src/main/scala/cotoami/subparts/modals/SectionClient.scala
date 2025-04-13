package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ActiveClient, Client, ClientNode, Id, Node}
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.subparts.{fieldInput, Modal}

object SectionClient {

  case class Model(
      nodeId: Id[Node],
      loading: Boolean = false,
      client: Option[Client] = None,
      generatingPassword: Boolean = false,
      generatingPasswordError: Option[String] = None
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

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.SectionClientMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ClientNodeFetched(result: Either[ErrorJson, ClientNode])
        extends Msg
    case class GenerateClientPassword(node: Node) extends Msg
    case class ClientPasswordGenerated(
        node: Node,
        result: Either[ErrorJson, String]
    ) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Cmd[AppMsg]) =
    msg match {
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
            generatingPasswordError = Some(e.default_message)
          ),
          cotoami.error("Couldn't generate a client password.", e)
        )
    }

  def apply(
      model: Model
  )(implicit context: Context): ReactElement =
    model.client.map { client =>
      section(className := "field-group pas-client")(
        h2()("As Client"),
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

  private def fieldLastLogin(client: Client)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.ModalNodeProfile_clientLastLogin,
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
      name = context.i18n.text.ModalNodeProfile_clientRemoteAddress,
      classes = "remote-address",
      inputValue = active.remoteAddr,
      readOnly = true
    )
}
