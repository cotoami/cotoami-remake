package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import marubinotto.fui.Cmd
import cotoami.{Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalAppUpdate {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model()

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.AppUpdateMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) = (model, Cmd.none)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    Modal.view(
      dialogClasses = "app-update",
      closeButton = Some((classOf[Modal.AppUpdate], dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("system_update_alt"),
        "Updating Application"
      )
    )(
      section(className := "update-details")(),
      div(className := "buttons")(
        button(
          `type` := "button",
          disabled := true
        )("Restart App")
      )
    )
  }
}
