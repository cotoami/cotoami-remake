package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html
import slinky.web.html._

import marubinotto.fui.{Cmd, Sub}
import marubinotto.libs.tauri

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.subparts.Modal

object ModalAppUpdate {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      appUpdate: tauri.updater.Update,
      contentLength: Double = 0,
      downloaded: Double = 0,
      finished: Boolean = false,
      restarting: Boolean = false
  ) {
    def progress(chunkLength: Double): Model =
      copy(downloaded = downloaded + chunkLength)

    lazy val readyToRestart: Boolean = finished && !restarting
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
    case class Started(contentLength: Double) extends Msg
    case class Progress(chunkLength: Double) extends Msg
    case object Finished extends Msg
    case object Restart extends Msg
    case class Restarted(result: Either[Throwable, Unit]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Cmd[AppMsg]) =
    msg match {
      case Msg.Started(contentLength) =>
        (model.copy(contentLength = contentLength), Cmd.none)

      case Msg.Progress(chunkLength) =>
        (model.progress(chunkLength), Cmd.none)

      case Msg.Finished => (model.copy(finished = true), Cmd.none)

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
  // Sub
  /////////////////////////////////////////////////////////////////////////////

  def progress(model: Model): Sub[AppMsg] =
    Sub.Impl[AppMsg](
      "app-update-progress",
      (dispatch, onSubscribe) => {
        model.appUpdate.downloadAndInstall(
          event =>
            event.event match {
              case "Started" =>
                event.data.foreach(
                  _.contentLength.foreach(length =>
                    dispatch(Msg.Started(length).into)
                  )
                )
              case "Progress" =>
                event.data.foreach(
                  _.chunkLength.foreach(length =>
                    dispatch(Msg.Progress(length).into)
                  )
                )
              case "Finished" => dispatch(Msg.Finished.into)
            },
          js.undefined
        )
        val unsubscribe = () => {
          println("Canceling app-update-progress ...")
          model.appUpdate.close() // fire-and-forget js.Promise[Unit]
          ()
        }
        onSubscribe(Some(unsubscribe))
      }
    )

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
          context.i18n.text.ModalAppUpdate_message(
            model.appUpdate.version,
            model.appUpdate.currentVersion
          )
        ),
        div(className := "progress-bar")(
          html.progress(
            value := model.downloaded.toString(),
            max := model.contentLength.toString()
          )
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
