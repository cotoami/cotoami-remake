package cotoami.backend

import scala.scalajs.js

@js.native
trait ChangelogEntryJson extends js.Object {
  val serial_number: Double = js.native
  val origin_node_id: String = js.native
  val origin_serial_number: Double = js.native
  val type_number: Int = js.native
  val change: ChangeJson = js.native
  val inserted_at: String = js.native
}

@js.native
trait ChangeJson extends js.Object {
  val None: js.UndefOr[scala.Null] = js.native
  val CreateNode: js.UndefOr[CreateNodeJson] = js.native
  val UpsertNode: js.UndefOr[NodeJson] = js.native
}

@js.native
trait CreateNodeJson extends js.Object {
  val node: NodeJson = js.native
  val root: js.Tuple2[CotonomaJson, CotoJson] = js.native
}
