package cotoami.backend

import scala.scalajs.js

import cotoami.models.DatabaseRole

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}

object DatabaseRoleBackend {
  def toModel(json: DatabaseRoleJson): DatabaseRole =
    json.Parent.toOption.map(parent =>
      DatabaseRole.Parent(ParentNodeBackend.toModel(parent))
    ).orElse(
      json.Child.toOption.map(child =>
        DatabaseRole.Child(ChildNodeBackend.toModel(child))
      )
    ).orNull // this should be unreachable
}
