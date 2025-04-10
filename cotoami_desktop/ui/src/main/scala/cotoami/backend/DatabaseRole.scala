package cotoami.backend

import scala.scalajs.js

import cotoami.models.DatabaseRole

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}

object DatabaseRoleBackend {
  def toModel(json: DatabaseRoleJson): DatabaseRole = {
    for (parent <- json.Parent.toOption) {
      return DatabaseRole.Parent(ParentNodeBackend.toModel(parent))
    }
    for (child <- json.Child.toOption) {
      return DatabaseRole.Child(ChildNodeBackend.toModel(child))
    }
    return null // this should be unreachable
  }
}
