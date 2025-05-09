package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._
import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Id, LocalNode, Node}
import cotoami.repository.Nodes
import cotoami.backend.{
  ClientNodeBackend,
  ErrorJson,
  LocalNodeBackend,
  LocalServer,
  ServerConfig
}
import cotoami.subparts.{sectionClientNodesCount, Modal}

object SectionSelfNodeServer {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      nodeId: Id[Node],
      loading: Boolean = false,
      selfNodeServer: Option[LocalServer] = None,
      clientCount: Double = 0,
      enablingAnonymousRead: Boolean = false
  )

  object Model {
    def apply(
        nodeId: Id[Node]
    )(implicit context: Context): (Model, Cmd[AppMsg]) =
      if (context.repo.nodes.isSelf(nodeId))
        (
          Model(nodeId, loading = true),
          Cmd.Batch(
            fetchClientCount,
            LocalServer.fetch.map(Msg.SelfNodeServerFetched(_).into)
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
      ModalNodeProfile.Msg.SectionSelfNodeServerMsg(this)
        .pipe(Modal.Msg.NodeProfileMsg)
        .pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class SelfNodeServerFetched(result: Either[ErrorJson, LocalServer])
        extends Msg
    case class ClientCountFetched(result: Either[ErrorJson, Double]) extends Msg
    case class EnableAnonymousRead(enable: Boolean) extends Msg
    case class AnonymousReadEnabled(result: Either[ErrorJson, LocalNode])
        extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) = {
    val default = (model, context.repo.nodes, Cmd.none)
    msg match {
      case Msg.SelfNodeServerFetched(Right(server)) =>
        default.copy(_1 =
          model.copy(selfNodeServer = Some(server), loading = false)
        )

      case Msg.SelfNodeServerFetched(Left(e)) =>
        default.copy(
          _1 = model.copy(loading = false),
          _3 = cotoami.error("Couldn't fetch the self node server.", e)
        )

      case Msg.ClientCountFetched(Right(count)) =>
        default.copy(_1 = model.copy(clientCount = count))

      case Msg.ClientCountFetched(Left(e)) =>
        default.copy(_3 = cotoami.error("Couldn't fetch client count.", e))

      case Msg.EnableAnonymousRead(enable) =>
        default.copy(
          _1 = model.copy(enablingAnonymousRead = true),
          _3 = LocalNodeBackend.enableAnonymousRead(enable)
            .map(Msg.AnonymousReadEnabled(_).into)
        )

      case Msg.AnonymousReadEnabled(Right(local)) =>
        default.copy(
          _1 = model
            .modify(_.enablingAnonymousRead).setTo(false)
            .modify(_.selfNodeServer.each.anonymousConnections).setTo(0),
          _2 = context.repo.nodes.modify(_.selfSettings).setTo(Some(local))
        )

      case Msg.AnonymousReadEnabled(Left(e)) =>
        default.copy(_3 =
          cotoami.error("Couldn't enable/disable anonymous read.", e)
        )
    }
  }

  def fetchClientCount: Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(0, Some(1))
      .map(_.map(_.totalItems))
      .map(Msg.ClientCountFetched(_).into)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Option.when(context.repo.nodes.isSelf(model.nodeId)) {
      model.selfNodeServer.flatMap(_.activeConfig)
        .map(sectionSelfNodeServer(_, model))
        .getOrElse(
          Option.when(model.loading) {
            section(
              className := "field-group self-node-server",
              aria - "busy" := model.loading.toString()
            )()
          }: ReactElement
        )
    }

  private def sectionSelfNodeServer(config: ServerConfig, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    section(className := "field-group self-node-server")(
      h2()(context.i18n.text.SelfNodeServer_title),
      fieldServerUrl(config),
      fieldClientNodes(model),
      fieldAnonymousRead(model)
    )

  private def fieldServerUrl(config: ServerConfig)(implicit
      context: Context
  ): ReactElement =
    fieldInput(
      name = context.i18n.text.SelfNodeServer_url,
      classes = "self-node-server-url",
      inputValue = config.url,
      readOnly = true
    )

  private def fieldClientNodes(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    field(
      name = context.i18n.text.SelfNodeServer_clientNodes,
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
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val anonymousReadEnabled =
      context.repo.nodes.selfSettings
        .map(_.anonymousReadEnabled)
        .getOrElse(false)
    field(
      name = context.i18n.text.SelfNodeServer_anonymousRead,
      classes = "anonymous-read"
    )(
      input(
        `type` := "checkbox",
        role := "switch",
        checked := anonymousReadEnabled,
        disabled := model.enablingAnonymousRead,
        onChange := (_ =>
          if (anonymousReadEnabled)
            dispatch(Msg.EnableAnonymousRead(false)) // disable
          else
            dispatch(
              Modal.Msg.OpenModal(
                Modal.Confirm(
                  context.i18n.text.SelfNodeServer_confirmEnableAnonymousRead,
                  Msg.EnableAnonymousRead(true) // enable
                )
              )
            )
        )
      ),
      Option.when(model.enablingAnonymousRead) {
        span(className := "processing", aria - "busy" := "true")()
      },
      Option.when(anonymousReadEnabled) {
        model.selfNodeServer.map(_.anonymousConnections).map(count =>
          span(className := "anonymous-connections")(
            s"(Active connections: ${count})"
          )
        )
      }
    )
  }
}
