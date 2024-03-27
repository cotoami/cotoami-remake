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
}
