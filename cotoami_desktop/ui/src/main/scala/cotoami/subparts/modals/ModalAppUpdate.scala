package cotoami.subparts.modals

import scala.util.chaining._
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html
import slinky.web.html._

import marubinotto.fui.Cmd
import marubinotto.libs.tauri

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalAppUpdate {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      appUpdate: tauri.updater.Update,
      restarting: Boolean = false
  ) {
    lazy val readyToRestart: Boolean = !restarting
  }

  object Model {
    def apply(appUpdate: tauri.updater.Update): (Model, Cmd[AppMsg]) =
      (Model(appUpdate = appUpdate), Cmd.none)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.AppUpdateMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case object Restart extends Msg
    case class Restarted(result: Either[Throwable, Unit]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Restart => (model.copy(restarting = true), restart)

      case Msg.Restarted(Right(_)) =>
        (model.copy(restarting = false), Cmd.none)

      case Msg.Restarted(Left(e)) =>
        (
          model.copy(restarting = false),
          cotoami.error("Couldn't restart app.", Some(e.toString()))
        )
    }

  private def restart: Cmd[AppMsg] =
    tauri.process.relaunch().toFuture
      .pipe(Cmd.fromFuture)
      .map(Msg.Restarted(_).into)

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def apply(
      model: Model
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    Modal.view(
      dialogClasses = "app-update",
      closeButton = Some((classOf[Modal.AppUpdate], dispatch))
    )(
      Fragment(
        Modal.spanTitleIcon("system_update_alt"),
        context.i18n.text.ModalAppUpdate_title
      )
    )(
      section(className := "update-details")(
        section(className := "message")(
          context.i18n.text.ModalAppUpdate_message("0.8.0", "0.7.0")
        ),
        div(className := "progress-bar")(
          html.progress(value := "50", max := "100")
        )
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          disabled := !model.readyToRestart,
          aria - "busy" := model.restarting.toString(),
          onClick := (_ => dispatch(Msg.Restart))
        )(context.i18n.text.ModalAppUpdate_restart)
      )
    )
  }
}
