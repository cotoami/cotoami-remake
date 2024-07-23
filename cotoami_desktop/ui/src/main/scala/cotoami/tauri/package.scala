package cotoami

import scala.util.{Failure, Success}

import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import cats.effect.IO

import fui.{Cmd, Sub}

package object tauri {

  // To distinguish a normal backend error from a system error during command invocation,
  // which will be returned as a string from Tauri, a backend error must be defined as an Object.
  def invokeCommand[T, E <: js.Object](
      command: String,
      args: js.Object = js.Dynamic.literal()
  ): Cmd[Either[E, T]] =
    Cmd(IO.async { cb =>
      IO {
        Tauri
          .invoke[T](command, args)
          // implicitly convert the Promise to a Future by
          // `scala.scalajs.js.Thenable.Implicits._`
          .onComplete {
            case Success(t) => {
              cb(Right(Some(Right(t))))
            }
            case Failure(ex: js.JavaScriptException) => {
              if (js.typeOf(ex.exception) == "object") {
                val error = ex.exception.asInstanceOf[E]
                cb(Right(Some(Left(error))))
              } else {
                // Tauri returns a string as an error when a system error occurred
                // during command invocation (ex. args deserialization error).
                cb(Left(new RuntimeException(ex.exception.toString())))
              }
            }
            case Failure(throwable) => {
              // should be unreachable
              cb(Left(throwable))
            }
          }
        None
      }
    })

  def listen[T](event: String, id: Option[String]): Sub[T] =
    Sub.Impl[T](
      id.getOrElse(s"listen-${event}"),
      (dispatch, onSubscribe) => {
        Event
          .listen(event, (e: Event[T]) => dispatch(e.payload))
          .foreach(unlisten => onSubscribe(Some(unlisten)))
      }
    )

  def selectSingleDirectory(
      dialogTitle: String,
      defaultDirectory: Option[String] = None
  ): Cmd[Either[Throwable, Option[String]]] =
    Cmd(IO.async { cb =>
      IO {
        val options = new Dialog.OpenDialogOptions {
          override val defaultPath = defaultDirectory match {
            case Some(path) => path
            case None       => ()
          }
          override val directory = true
          override val multiple = false
          override val recursive = true
          override val title = dialogTitle
        }
        Dialog
          .open(options)
          // implicitly convert the Promise to a Future by
          // `scala.scalajs.js.Thenable.Implicits._`
          .onComplete {
            case Success(result) => {
              val path = if (result == null) None else Some(result.toString())
              cb(Right(Some(Right(path))))
            }
            case Failure(throwable) => {
              cb(Right(Some(Left(throwable))))
            }
          }
        None
      }
    })
}
