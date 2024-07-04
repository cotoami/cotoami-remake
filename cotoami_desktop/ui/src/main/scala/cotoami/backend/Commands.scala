package cotoami.backend

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import fui.Cmd
import cotoami.tauri

object Commands {

  def send[T](command: js.Object): Cmd[Either[ErrorJson, T]] =
    tauri.invokeCommand(
      "node_command",
      js.Dynamic.literal(command = command)
    ).map((e: Either[ErrorJson, String]) =>
      e.map(js.JSON.parse(_).asInstanceOf[T])
    )

  val LocalNode = jso(LocalNode = null)

  def TryConnectServerNode(
      url_prefix: String,
      password: String,
      client_role: Option[String] = None
  ) =
    jso(TryConnectServerNode =
      jso(
        url_prefix = url_prefix,
        password = password,
        client_role = client_role.getOrElse(null)
      )
    )

  def AddServerNode(
      url_prefix: String,
      password: String,
      client_role: Option[String] = None
  ) =
    jso(AddServerNode =
      jso(
        url_prefix = url_prefix,
        password = password,
        client_role = client_role.getOrElse(null)
      )
    )

  def UpdateServerNode(
      id: Id[Node],
      disabled: Option[Boolean],
      url_prefix: Option[String]
  ) =
    jso(UpdateServerNode =
      jso(
        id = id.uuid,
        values = jso(
          // For some reason, `disabled.getOrElse(null)` causes a compile error
          // without explicit type conversion (`asInstanceOf`).
          disabled = disabled.getOrElse(null).asInstanceOf[js.Any],
          url_prefix = url_prefix.getOrElse(null)
        )
      )
    )

  def RecentCotonomas(nodeId: Option[Id[Node]], pageIndex: Double) =
    jso(RecentCotonomas =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def Cotonoma(id: Id[Cotonoma]) =
    jso(Cotonoma = jso(id = id.uuid))

  def CotonomaDetails(id: Id[Cotonoma]) =
    jso(CotonomaDetails = jso(id = id.uuid))

  def CotonomaByName(name: String, node: Id[Node]) =
    jso(CotonomaByName = jso(name = name, node = node.uuid))

  def SubCotonomas(id: Id[Cotonoma], pageIndex: Double) =
    jso(SubCotonomas = jso(id = id.uuid, pagination = jso(page = pageIndex)))

  def RecentCotos(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ) =
    jso(RecentCotos =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def SearchCotos(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ) =
    jso(SearchCotos =
      jso(
        query = query,
        node = nodeId.map(_.uuid).getOrElse(null),
        cotonoma = cotonomaId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def GraphFromCoto(coto: Id[Coto]) =
    jso(GraphFromCoto = jso(coto = coto.uuid))

  def GraphFromCotonoma(cotonoma: Id[Cotonoma]) =
    jso(GraphFromCotonoma = jso(cotonoma = cotonoma.uuid))

  def PostCoto(
      content: String,
      summary: Option[String],
      post_to: Id[Cotonoma]
  ) =
    jso(PostCoto =
      jso(
        input = jso(content = content, summary = summary.getOrElse(null)),
        post_to = post_to.uuid
      )
    )

  def PostCotonoma(name: String, post_to: Id[Cotonoma]) =
    jso(PostCotonoma =
      jso(
        input = jso(name = name),
        post_to = post_to.uuid
      )
    )
}
