package cotoami.backend

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{literal => jso}
import fui.FunctionalUI._
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
}
