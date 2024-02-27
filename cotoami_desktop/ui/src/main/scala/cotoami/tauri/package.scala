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
}
