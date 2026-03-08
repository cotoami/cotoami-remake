package cotoami.models

enum Scope {
  case All
  case ByNode(nodeId: Id[Node])
  case ByCotonoma(
      cotonomaId: Id[Cotonoma],
      scope: CotonomaScope = CotonomaScope.Recursive
  )
}

enum CotonomaScope {
  case Local
  case Recursive
  case Depth(value: Int)
}
