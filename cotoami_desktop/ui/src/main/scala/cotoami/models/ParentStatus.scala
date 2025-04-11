package cotoami.models

sealed trait ParentStatus {
  def disabled: Boolean =
    this match {
      case ParentStatus.ServerDisconnected(Server.NotConnected.Disabled) => true
      case _ => false
    }
}

object ParentStatus {
  case class Connected(child: Option[ChildNode]) extends ParentStatus
  case class ServerDisconnected(details: Server.NotConnected)
      extends ParentStatus
}
