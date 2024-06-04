package cotoami.backend

import scala.scalajs.js
import java.time.Instant
import cotoami.utils.Validation

case class Node(json: NodeJson) {
  def id: Id[Node] = Id(this.json.uuid)
  def icon: String = this.json.icon
  def name: String = this.json.name
  def rootCotonomaId: Id[Cotonoma] = Id(this.json.root_cotonoma_id)
  def version: Int = this.json.version
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)
}

@js.native
trait NodeJson extends js.Object {
  val uuid: String = js.native
  val icon: String = js.native // Base64 encoded image binary
  val name: String = js.native
  val root_cotonoma_id: String = js.native
  val version: Int = js.native
  val created_at: String = js.native
}

sealed trait DatabaseRole
case class Parent(info: ParentNode) extends DatabaseRole
case class Child(info: ChildNode) extends DatabaseRole

object DatabaseRole {
  def apply(json: DatabaseRoleJson): DatabaseRole = {
    for (parent <- json.Parent.toOption) {
      return Parent(ParentNode(parent))
    }
    for (child <- json.Child.toOption) {
      return Child(ChildNode(child))
    }
    return null // this should be unreachable
  }
}

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}
