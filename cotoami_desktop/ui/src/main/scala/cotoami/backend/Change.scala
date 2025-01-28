package cotoami.backend

import scala.scalajs.js
import cotoami.utils.facade.Nullable

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
  val RenameNode: js.UndefOr[RenameNodeJson] = js.native
  val SetNodeIcon: js.UndefOr[SetNodeIconJson] = js.native
  val SetRootCotonoma: js.UndefOr[SetRootCotonomaJson] = js.native
  val CreateCoto: js.UndefOr[CotoJson] = js.native
  val EditCoto: js.UndefOr[EditCotoJson] = js.native
  val DeleteCoto: js.UndefOr[DeleteCotoJson] = js.native
  val CreateCotonoma: js.UndefOr[js.Tuple2[CotonomaJson, CotoJson]] = js.native
  val RenameCotonoma: js.UndefOr[RenameCotonomaJson] = js.native
  val CreateLink: js.UndefOr[LinkJson] = js.native
  val EditLink: js.UndefOr[EditLinkJson] = js.native
  val DeleteLink: js.UndefOr[DeleteLinkJson] = js.native
  val ChangeOwnerNode: js.UndefOr[ChangeOwnerNodeJson] = js.native
}

@js.native
trait CreateNodeJson extends js.Object {
  val node: NodeJson = js.native
  val root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] = js.native
}

@js.native
trait RenameNodeJson extends js.Object {
  val node_id: String = js.native
  val name: String = js.native
  val updated_at: String = js.native
}

@js.native
trait SetNodeIconJson extends js.Object {
  val node_id: String = js.native
  val icon: String = js.native // Base64 encoded image binary
}

@js.native
trait SetRootCotonomaJson extends js.Object {
  val node_id: String = js.native
  val cotonoma_id: String = js.native
}

@js.native
trait EditCotoJson extends js.Object {
  val coto_id: String = js.native
  val diff: js.Object = js.native
  val updated_at: String = js.native
}

@js.native
trait DeleteCotoJson extends js.Object {
  val coto_id: String = js.native
  val deleted_at: String = js.native
}

@js.native
trait RenameCotonomaJson extends js.Object {
  val cotonoma_id: String = js.native
  val name: String = js.native
  val updated_at: String = js.native
}

@js.native
trait EditLinkJson extends js.Object {
  val link_id: String = js.native
  val diff: js.Object = js.native
  val updated_at: String = js.native
}

@js.native
trait DeleteLinkJson extends js.Object {
  val link_id: String = js.native
}

@js.native
trait ChangeOwnerNodeJson extends js.Object {
  val from: String = js.native
  val to: String = js.native
  val last_change_number: Double = js.native
}
