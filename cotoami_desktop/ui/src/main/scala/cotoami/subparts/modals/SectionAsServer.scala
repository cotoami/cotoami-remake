package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, Node}
import cotoami.backend.{
  ClientNodeBackend,
  Commands,
  ErrorJson,
  LocalServer,
  ServerConfig
}
import cotoami.subparts.{
  buttonEdit,
  field,
  fieldInput,
  sectionClientNodesCount,
  Modal
}

object SectionAsServer {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      loading: Boolean = false,
      localServer: Option[LocalServer] = None,
      clientCount: Double = 0,
      enablingAnonymousRead: Boolean = false
  ) {
    def anonymousReadEnabled: Boolean =
      localServer.map(_.anonymousReadEnabled).getOrElse(false)
  }

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (context.repo.nodes.isOperating(nodeId))
        (
          Model(nodeId, loading = true),
          Cmd.Batch(
            fetchClientCount,
            LocalServer.fetch.map(Msg.LocalServerFetched(_).into)
          )
        )
      else
        (Model(nodeId), Cmd.none)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into =
      ModalNodeProfile.Msg.SectionAsServerMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class LocalServerFetched(result: Either[ErrorJson, LocalServer])
        extends Msg
    case class ClientCountFetched(result: Either[ErrorJson, Double]) extends Msg
    case class EnableAnonymousRead(enable: Boolean) extends Msg
    case class AnonymousReadEnabled(result: Either[ErrorJson, Boolean])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.LocalServerFetched(Right(server)) =>
        (
          model.copy(localServer = Some(server), loading = false),
          Cmd.none
        )

      case Msg.LocalServerFetched(Left(e)) =>
        (
          model.copy(loading = false),
          cotoami.error("Couldn't fetch the local server.", e)
        )

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

  def apply(
      model: Model
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    model.localServer.flatMap(_.activeConfig).map { config =>
      section(className := "field-group local-server")(
        h2()(context.i18n.text.AsServer_title),
        fieldLocalServerUrl(config),
        fieldClientNodes(model),
        fieldAnonymousRead(model)
      )
    }.getOrElse(
      Option.when(model.loading) {
        section(
          className := "field-group local-server",
          aria - "busy" := model.loading.toString()
        )()
      }
    )

  private def fieldLocalServerUrl(config: ServerConfig)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.AsServer_url,
      classes = "local-server-url",
      inputValue = config.url,
      readOnly = true
    )

  private def fieldClientNodes(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.AsServer_clientNodes,
      classes = "client-nodes"
    )(
      sectionClientNodesCount(model.clientCount, context.repo.nodes),
      div(className := "edit")(
        buttonEdit(_ =>
          dispatch((Modal.Msg.OpenModal.apply _).tupled(Modal.Clients()))
        )
      )
    )

  private def fieldAnonymousRead(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    field(
      name = context.i18n.text.AsServer_anonymousRead,
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
                  context.i18n.text.AsServer_confirmEnableAnonymousRead,
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
}
