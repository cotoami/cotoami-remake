package cotoami.models

sealed trait Scope

object Scope {
  case object All extends Scope
  case class ByNode(nodeId: Id[Node]) extends Scope
  case class ByCotonoma(
      cotonomaId: Id[Cotonoma],
      scope: CotonomaScope = CotonomaScope.Recursive
  ) extends Scope
}

sealed trait CotonomaScope

object CotonomaScope {
  case object Local extends CotonomaScope
  case object Recursive extends CotonomaScope
  case class Depth(value: Int) extends CotonomaScope
}
