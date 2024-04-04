package cotoami.backend

import scala.scalajs.js.Dynamic.{literal => jso}
import cotoami.Id

object Commands {

  val LocalNode = jso(LocalNode = null)

  def RecentCotonomas(nodeId: Option[Id[Node]], pageIndex: Double) =
    jso(RecentCotonomas =
      jso(
        node = nodeId.map(_.uuid).getOrElse(null),
        pagination = jso(page = pageIndex)
      )
    )

  def Cotonoma(id: Id[Cotonoma]) = jso(Cotonoma = jso(id = id.uuid))

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
}
