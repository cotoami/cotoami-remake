package cotoami.subparts.modals

import scala.util.chaining._
import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{ActiveClient, ClientNode, Id, Node, Page, PaginatedItems}
import cotoami.repositories.Nodes
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.components.{materialSymbol, toolButton}
import cotoami.subparts.{sectionClientNodesCount, spanNode, Modal}

object ModalClients {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      clientNodes: PaginatedItems[ClientNode] = PaginatedItems(),
      togglingDisabled: Set[Id[Node]] = Set.empty,
      error: Option[String] = None
  ) {
    def clients(nodes: Nodes): Seq[Client] =
      clientNodes.items.flatMap { client =>
        nodes.get(client.nodeId).map { node =>
          Client(node, client, nodes.activeClients.get(client.nodeId))
        }
      }

    def update(client: ClientNode): Model =
      this.modify(_.clientNodes.items.eachWhere(_.nodeId == client.nodeId))
        .setTo(client)
  }

  case class Client(
      node: Node,
      client: ClientNode,
      active: Option[ActiveClient]
  )

  object Model {
    def apply(): (Model, Cmd[AppMsg]) =
      (
        Model(),
        fetchClients(0)
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ClientsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ClientsFetched(result: Either[ErrorJson, Page[ClientNode]])
        extends Msg
    case class SetDisabled(id: Id[Node], disable: Boolean) extends Msg
    case class ClientUpdated(
        id: Id[Node],
        result: Either[ErrorJson, ClientNode]
    ) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.ClientsFetched(Right(page)) =>
        (model.modify(_.clientNodes).using(_.appendPage(page)), Cmd.none)

      case Msg.ClientsFetched(Left(e)) =>
        (model, cotoami.error("Couldn't fetch clients.", e))

      case Msg.SetDisabled(id, disable) =>
        (
          model.modify(_.togglingDisabled).using(_ + id),
          ClientNodeBackend.update(id, Some(disable))
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

  def fetchClients(pageIndex: Double): Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(pageIndex)
      .map(Msg.ClientsFetched(_).into)

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
      "Client nodes"
    )(
      header()(
        toolButton(
          symbol = "add",
          tip = "Add client",
          tipPlacement = "right",
          classes = "add",
          onClick = _ => dispatch(Modal.Msg.OpenModal(Modal.NewClient()))
        ),
        sectionClientNodesCount(
          model.clientNodes.totalItems,
          context.domain.nodes
        )
      ),
      div(className := "body")(
        model.clients(context.domain.nodes) match {
          case Seq() =>
            div(className := "empty")(
              "No client nodes registered yet."
            )
          case clients =>
            table(className := "client-nodes", role := "grid")(
              thead()(
                tr()(
                  th()("Node ID"),
                  th()("Name"),
                  th()("Status"),
                  th()("Enabled"),
                  th()()
                )
              ),
              tbody()(
                clients.map(trClient(_, model))
              )
            )
        }
      )
    )

  private def trClient(client: Client, model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    tr()(
      td(className := "id")(
        code()(client.node.id.uuid)
      ),
      td(className := "name")(
        if (client.node.name.isBlank())
          span(className := "not-yet-connected")(
            "<Not yet connected>"
          )
        else
          spanNode(client.node)
      ),
      td(className := "status")(
        if (client.active.isDefined)
          span(
            className := "status connected",
            data - "tooltip" := "Connected",
            data - "placement" := "bottom"
          )(
            materialSymbol("link")
          )
        else
          span(
            className := "status disconnected",
            data - "tooltip" := "Disconnected",
            data - "placement" := "bottom"
          )(
            materialSymbol("do_not_disturb_on")
          )
      ),
      td(className := "enabled")(
        input(
          `type` := "checkbox",
          role := "switch",
          checked := !client.client.disabled,
          disabled := (
            model.togglingDisabled.contains(client.node.id) ||
              // Avoid disabling oneself unintentionally when operating as a parent node
              Some(client.node.id) == context.domain.nodes.localId
          ),
          onChange := (_ =>
            dispatch(Msg.SetDisabled(client.node.id, !client.client.disabled))
          )
        )
      ),
      td(className := "settings")(
        toolButton(
          symbol = "settings",
          tip = "Settings",
          tipPlacement = "bottom",
          onClick = _ => ()
        )
      )
    )
}
