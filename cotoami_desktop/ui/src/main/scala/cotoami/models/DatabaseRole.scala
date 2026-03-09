package cotoami.models

enum DatabaseRole {
  case Parent(info: ParentNode)
  case Child(info: ChildNode)
}
