package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.materialSymbol
import cotoami.models.ParentStatus
import cotoami.models.Server.NotConnected

case class ViewParentStatus(
    className: String,
    icon: ReactElement,
    title: String,
    message: Option[String]
)

object ViewParentStatus {
  def apply(status: ParentStatus): Option[ViewParentStatus] =
    status match {
      case ParentStatus.ServerDisconnected(details) =>
        details match {
          case NotConnected.Disabled =>
            Some(
              ViewParentStatus(
                "disabled",
                materialSymbol("link_off"),
                "not synced",
                None
              )
            )
          case NotConnected.Connecting(message) =>
            Some(
              ViewParentStatus(
                "connecting",
                span(className := "busy", aria - "busy" := "true")(),
                "connecting",
                message
              )
            )
          case NotConnected.InitFailed(message) =>
            Some(
              ViewParentStatus(
                "error init-failed",
                materialSymbol("error"),
                "initialization failed",
                Some(message)
              )
            )
          case NotConnected.Unauthorized =>
            Some(
              ViewParentStatus(
                "error authentication-failed",
                materialSymbol("error"),
                "authentication failed",
                None
              )
            )
          case NotConnected.SessionExpired =>
            Some(
              ViewParentStatus(
                "error session-expired",
                materialSymbol("error"),
                "session expired",
                None
              )
            )
          case NotConnected.Disconnected(message) =>
            Some(
              ViewParentStatus(
                "disconnected",
                materialSymbol("do_not_disturb_on"),
                "disconnected",
                message
              )
            )
        }

      case ParentStatus.Connected(_) => None
    }

}
