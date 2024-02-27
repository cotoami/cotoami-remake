package cotoami

import scala.util.{Success, Failure}

import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import cats.effect.IO

import fui.FunctionalUI.Cmd

package object tauri {

  def invokeCommand[Msg, Result](
      createMsg: Either[Throwable, Result] => Msg,
      command: String,
      args: js.Object = js.Dynamic.literal()
  ): Cmd[Msg] = {
    IO.async { cb =>
      IO {
        Tauri
          .invoke[Result](command, args)
          .onComplete {
            case Success(r) => {
              cb(Right(Some(createMsg(Right(r)))))
            }
            case Failure(t) => {
              cb(Right(Some(createMsg(Left(t)))))
            }
          }
        None
      }
    }
  }

  def selectSingleDirectory[Msg](
      dialogTitle: String,
      createMsg: Either[Throwable, Option[String]] => Msg
  ): Cmd[Msg] = {
    IO.async { cb =>
      IO {
        val options = new Dialog.OpenDialogOptions {
          override val directory = true
          override val multiple = false
          override val recursive = true
          override val title = dialogTitle
        }
        Dialog
          .open(options)
          .onComplete {
            case Success(r) => {
              val path = if (r == null) None else Some(r.toString())
              cb(Right(Some(createMsg(Right(path)))))
            }
            case Failure(t) => {
              cb(Right(Some(createMsg(Left(t)))))
            }
          }
        None
      }
    }
  }
}
