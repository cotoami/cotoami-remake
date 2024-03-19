package cotoami.backend

import scala.scalajs.js.Dynamic.{literal => jso}

object Commands {

  val LocalNode = jso(LocalNode = null)

  def RecentCotonomas(nodeId: Option[String]) =
    jso(RecentCotonomas =
      jso(node = nodeId.getOrElse(null), pagination = jso(page = 0))
    )
}
