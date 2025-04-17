package cotoami.backend

import scala.scalajs.js

import marubinotto.facade.Nullable
import cotoami.models.{Id, LocalNode}

@js.native
trait LocalNodeJson extends js.Object {
  val node_id: String = js.native
  val image_max_size: Nullable[Int] = js.native
  val anonymous_read_enabled: Boolean = js.native
}

object LocalNodeBackend {
  def toModel(json: LocalNodeJson): LocalNode =
    LocalNode(
      nodeId = Id(json.node_id),
      imageMaxSize = Nullable.toOption(json.image_max_size),
      anonymousReadEnabled = json.anonymous_read_enabled
    )
}
