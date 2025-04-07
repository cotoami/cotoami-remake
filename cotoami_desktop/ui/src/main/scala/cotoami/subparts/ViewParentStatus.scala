package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.components.materialSymbol
import cotoami.models.ParentStatus

case class ViewParentStatus(
    className: String,
    icon: ReactElement,
    title: String,
    message: Option[String]
)

object ViewParentStatus {
  def apply(status: ParentStatus): Option[ViewParentStatus] =
    status match {
      case ParentStatus.Disabled =>
        Some(
          ViewParentStatus(
            "disabled",
            materialSymbol("link_off"),
            "not synced",
            None
          )
        )
      case ParentStatus.Connecting(message) =>
        Some(
          ViewParentStatus(
            "connecting",
            span(className := "busy", aria - "busy" := "true")(),
            "connecting",
            message
          )
        )
      case ParentStatus.InitFailed(message) =>
        Some(
          ViewParentStatus(
            "init-failed",
            materialSymbol("error"),
            "initialization failed",
            Some(message)
          )
        )
      case ParentStatus.Disconnected(message) =>
        Some(
          ViewParentStatus(
            "disconnected",
            materialSymbol("do_not_disturb_on"),
            "disconnected",
            message
          )
        )
      case _ => None
    }
}
