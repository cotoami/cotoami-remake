package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.materialSymbol
import cotoami.Context
import cotoami.models.{ParentStatus, Server}
import cotoami.models.Server.NotConnected

case class ViewConnectionStatus(
    className: String,
    icon: ReactElement,
    title: String,
    message: Option[String]
) {
  def onlyIfNotConnected: Option[ViewConnectionStatus] =
    Option.when(className != ViewConnectionStatus.ConnectedClassName)(this)
}

object ViewConnectionStatus {
  val ConnectedClassName = "connected"

  def connected(implicit context: Context) =
    ViewConnectionStatus(
      ConnectedClassName,
      materialSymbol("link"),
      context.i18n.text.Connection_connected,
      None
    )

  def apply(server: Server)(implicit context: Context): ViewConnectionStatus =
    server.notConnected.map(apply).getOrElse(connected)

  def apply(
      status: ParentStatus
  )(implicit context: Context): ViewConnectionStatus =
    status match {
      case ParentStatus.ServerDisconnected(details) => apply(details)
      case ParentStatus.Connected(_)                => connected
    }

  def apply(
      notConnected: NotConnected
  )(implicit context: Context): ViewConnectionStatus =
    notConnected match {
      case NotConnected.Disabled =>
        ViewConnectionStatus(
          "disabled",
          materialSymbol("link_off"),
          context.i18n.text.Connection_disabled,
          None
        )
      case NotConnected.Connecting(message) =>
        ViewConnectionStatus(
          "connecting",
          span(className := "busy", aria - "busy" := "true")(),
          context.i18n.text.Connection_connecting,
          message
        )
      case NotConnected.InitFailed(message) =>
        ViewConnectionStatus(
          "error init-failed",
          materialSymbol("error"),
          context.i18n.text.Connection_initFailed,
          Some(message)
        )
      case NotConnected.Unauthorized =>
        ViewConnectionStatus(
          "error authentication-failed",
          materialSymbol("error"),
          context.i18n.text.Connection_authenticationFailed,
          None
        )
      case NotConnected.SessionExpired =>
        ViewConnectionStatus(
          "error session-expired",
          materialSymbol("error"),
          context.i18n.text.Connection_sessionExpired,
          None
        )
      case NotConnected.Disconnected(message) =>
        ViewConnectionStatus(
          "disconnected",
          materialSymbol("do_not_disturb_on"),
          context.i18n.text.Connection_disconnected,
          message
        )
    }
}
