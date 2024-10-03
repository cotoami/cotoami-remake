package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import com.softwaremill.quicklens._

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Into, Msg => AppMsg}
import cotoami.models.{ActiveClient, ClientNode, Node, Page, PaginatedItems}
import cotoami.repositories.Nodes
import cotoami.backend.{ClientNodeBackend, ErrorJson}
import cotoami.components.toolButton
import cotoami.subparts.Modal

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
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    Modal.view(
      elementClasses = "client-nodes header-and-body",
      closeButton = Some((classOf[Modal.Clients], dispatch)),
      error = model.error
    )(
      "Client nodes"
    )(
      header()(
        toolButton(
          symbol = "add",
          tip = "Add node",
          tipPlacement = "bottom",
          classes = "add",
          onClick = _ => ()
        )
      ),
      div(className := "body")(
        //
      )
    )
}
