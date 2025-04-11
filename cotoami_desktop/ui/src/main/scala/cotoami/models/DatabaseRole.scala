package cotoami.models

sealed trait DatabaseRole

object DatabaseRole {
  case class Parent(info: ParentNode) extends DatabaseRole
  case class Child(info: ChildNode) extends DatabaseRole
}
