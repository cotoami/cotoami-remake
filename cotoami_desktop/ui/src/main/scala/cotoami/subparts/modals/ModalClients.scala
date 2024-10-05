package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Context, Into, Msg => AppMsg}
import cotoami.models.{ActiveClient, ClientNode, Node, Page, PaginatedItems}
import cotoami.repositories.Nodes
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.components.{materialSymbol, toolButton}
import cotoami.subparts.{sectionClientNodesCount, spanNode, Modal}

object ModalClients {

  case class Model(
      clientNodes: PaginatedItems[ClientNode] = PaginatedItems(),
      error: Option[String] = None
  ) {
    def clients(nodes: Nodes): Seq[Client] =
      clientNodes.items.flatMap { client =>
        nodes.get(client.nodeId).map { node =>
          Client(node, client, nodes.activeClients.get(client.nodeId))
        }
      }
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

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.ClientsMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ClientsFetched(result: Either[ErrorJson, Page[ClientNode]])
        extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = {
    msg match {
      case Msg.ClientsFetched(Right(page)) =>
        (model.modify(_.clientNodes).using(_.appendPage(page)), Cmd.none)

      case Msg.ClientsFetched(Left(e)) =>
        (
          model,
          log_error("Couldn't fetch clients.", Some(js.JSON.stringify(e)))
        )
    }
  }

  def fetchClients(pageIndex: Double): Cmd.One[AppMsg] =
    ClientNodeBackend.fetchRecent(pageIndex)
      .map(Msg.ClientsFetched(_).into)

  def apply(model: Model)(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      dialogClasses = "client-nodes",
      closeButton = Some((classOf[Modal.Clients], dispatch)),
      error = model.error,
      bodyClasses = "header-and-body"
    )(
      "Client nodes"
    )(
      header()(
        toolButton(
          symbol = "add",
          tip = "Add node",
          tipPlacement = "right",
          classes = "add",
          onClick = _ => ()
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
                clients.map(trClient)
              )
            )
        }
      )
    )

  private def trClient(client: Client): ReactElement =
    tr()(
      td(className := "id")(client.node.id.uuid),
      td(className := "name")(spanNode(client.node)),
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
          onChange := (_ => ())
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
