package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{materialSymbol, toolButton, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Client, ClientNode, Id, Node, Page, PaginatedItems}
import cotoami.repository.Nodes
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.subparts.{sectionClientNodesCount, Modal, PartsNode}

object ModalClients {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      clientNodes: PaginatedItems[ClientNode] = PaginatedItems(),
      loading: Boolean = false,
      togglingDisabled: Set[Id[Node]] = Set.empty,
      error: Option[String] = None
  ) {
    def fetchFirst: (Model, Cmd.One[AppMsg]) = fetch(0)

    def fetchMore: (Model, Cmd.One[AppMsg]) =
      if (loading)
        (this, Cmd.none)
      else
        clientNodes.nextPageIndex
          .map(fetch)
          .getOrElse((this, Cmd.none)) // no more

    private def fetch(pageIndex: Double): (Model, Cmd.One[AppMsg]) =
      (
        copy(loading = true),
        ClientNodeBackend.fetchRecent(pageIndex)
          .map(Msg.Fetched(_).into)
      )

    def appendPage(page: Page[ClientNode]): Model =
      this
        .modify(_.loading).setTo(false)
        .modify(_.clientNodes).using(_.appendPage(page))

    def clients(nodes: Nodes): Seq[Client] =
      clientNodes.items.flatMap(nodes.clientInfo)

    def update(client: ClientNode): Model =
      this.modify(_.clientNodes.items.eachWhere(_.nodeId == client.nodeId))
        .setTo(client)
  }

  object Model {
    def apply(): (Model, Cmd[AppMsg]) = new Model().fetchFirst
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ClientsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object FetchFirst extends Msg
    case object FetchMore extends Msg
    case class Fetched(result: Either[ErrorJson, Page[ClientNode]]) extends Msg
    case class SetDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class ClientUpdated(
        id: Id[Node],
        result: Either[ErrorJson, ClientNode]
    ) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.FetchFirst => model.fetchFirst

      case Msg.FetchMore => model.fetchMore

      case Msg.Fetched(Right(page)) =>
        (model.appendPage(page), Cmd.none)

      case Msg.Fetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch clients.", e))

      case Msg.SetDisabled(id, disable) =>
        (
          model.modify(_.togglingDisabled).using(_ + id),
          ClientNodeBackend.edit(id, Some(disable))
            .map(Msg.ClientUpdated(id, _).into)
        )

      case Msg.ClientUpdated(id, Right(client)) =>
        (
          model
            .modify(_.togglingDisabled).using(_ - id)
            .update(client),
          Cmd.none
        )

      case Msg.ClientUpdated(id, Left(e)) =>
        (
          model.modify(_.togglingDisabled).using(_ - id),
          cotoami.error("Couldn't update a client.", e)
        )
    }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "clients",
      closeButton = Some((classOf[Modal.Clients], dispatch)),
      error = model.error,
      bodyClasses = "header-and-body"
    )(
      context.i18n.text.ModalClients_title
    )(
      header()(
        button(
          className := "add contrast outline",
          onClick := (_ => dispatch(Modal.Msg.OpenModal(Modal.NewClient())))
        )(
          materialSymbol("add"),
          context.i18n.text.ModalClients_add
        ),
        sectionClientNodesCount(
          model.clientNodes.totalItems,
          context.repo.nodes
        )
      ),
      div(className := "body")(
        sectionClientNodes(model)
      )
    )

  private def sectionClientNodes(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    model.clients(context.repo.nodes) match {
      case Seq() =>
        section(className := "client-nodes empty")(
          "No client nodes registered yet."
        )
      case clients =>
        section(className := "client-nodes table")(
          div(className := "table-header")(
            div(className := "column name")("Name"),
            div(className := "column last-login")("Last Login"),
            div(className := "column status")("Status"),
            div(className := "column enabled")("Enabled"),
            div(className := "column settings")()
          ),
          div(className := "table-body")(
            ScrollArea(
              onScrollToBottom = Some(() => dispatch(Msg.FetchMore))
            )(
              (
                clients.map(divClientRow(_, model)) :+
                  div(
                    className := "more",
                    aria - "busy" := model.loading.toString()
                  )()
              ): _*
            )
          )
        )
    }

  private def divClientRow(client: Client, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement = {
    val isLocal = Some(client.node.id) == context.repo.nodes.localId
    div(key := client.node.id.uuid, className := "row client")(
      div(
        className := optionalClasses(
          Seq(
            ("column", true),
            ("name", true),
            ("not-yet-connected", client.node.name.isBlank())
          )
        )
      )(
        if (client.node.name.isBlank())
          code()(client.node.id.uuid)
        else
          PartsNode.spanNode(client.node),
        Option.when(isLocal) {
          span(className := "local-node-mark")("(You)")
        }
      ),
      div(className := "column last-login")(
        client.client.lastSessionCreatedAt
          .map(context.time.display)
          .getOrElse("-"): String
      ),
      div(className := "column status")(
        if (client.active.isDefined)
          span(
            className := "status connected",
            data - "tooltip" := "Connected",
            data - "placement" := "right"
          )(
            materialSymbol("link")
          )
        else
          span(
            className := "status disconnected",
            data - "tooltip" := "Disconnected",
            data - "placement" := "right"
          )(
            materialSymbol("do_not_disturb_on")
          )
      ),
      div(className := "column enabled")(
        input(
          `type` := "checkbox",
          role := "switch",
          checked := !client.client.disabled,
          disabled := (
            model.togglingDisabled.contains(client.node.id) ||
              // Avoid disabling oneself unintentionally when operating as a parent node
              isLocal
          ),
          onChange := (_ =>
            dispatch(Msg.SetDisabled(client.node.id, !client.client.disabled))
          )
        )
      ),
      div(className := "column settings")(
        toolButton(
          symbol = "settings",
          disabled = isLocal,
          onClick = _ =>
            dispatch(
              (Modal.Msg.OpenModal.apply _).tupled(
                Modal.NodeProfile(client.node.id)
              )
            )
        )
      )
    )
  }
}
