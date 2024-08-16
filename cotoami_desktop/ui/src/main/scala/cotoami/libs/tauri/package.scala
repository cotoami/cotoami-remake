package cotoami.libs

import scala.util.{Failure, Success}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.Thenable.Implicits._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import cats.effect.IO

import fui.{Cmd, Sub}

package object tauri {

  /** Convert a device file path to an URL that can be loaded by the webview.
    */
  @js.native
  @JSImport("@tauri-apps/api/tauri", "convertFileSrc")
  def convertFileSrc(
      filePath: String,
      protocol: js.UndefOr[String] = js.undefined
  ): String =
    js.native

  /** Sends a message to the backend.
    *
    * <https://tauri.app/v1/api/js/tauri#invoke>
    *
    * @param cmd
    *   The command name.
    * @param args
    *   The optional arguments to pass to the command. It should be passed as a
    *   JSON object with camelCase keys (when declaring arguments in Rust using
    *   snake_case, the arguments are converted to camelCase for JavaScript).
    *   For the Rust side, it can be of any type, as long as they implement
    *   `serde::Deserialize`.
    * @return
    *   A promise resolving or rejecting to the backend response. For the Rust
    *   side, the returned data can be of any type, as long as it implements
    *   `serde::Serialize`.
    */
  @js.native
  @JSImport("@tauri-apps/api/tauri", "invoke")
  def invoke[T](
      cmd: String,
      args: js.Object = js.Dynamic.literal()
  ): js.Promise[T] = js.native

  // To distinguish a normal backend error from a system error during command invocation,
  // which will be returned as a string from Tauri, a backend error must be defined as an Object.
  def invokeCommand[T, E <: js.Object](
      command: String,
      args: js.Object = js.Dynamic.literal()
  ): Cmd[Either[E, T]] =
    Cmd(IO.async { cb =>
      IO {
        invoke[T](command, args)
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
        tauri.event
          .listen(event, (e: tauri.event.Event[T]) => dispatch(e.payload))
          .foreach(unlisten => onSubscribe(Some(unlisten)))
      }
    )

  def selectSingleDirectory(
      dialogTitle: String,
      defaultDirectory: Option[String] = None
  ): Cmd[Either[Throwable, Option[String]]] =
    Cmd(IO.async { cb =>
      IO {
        val options = new dialog.OpenDialogOptions {
          override val defaultPath = defaultDirectory match {
            case Some(path) => path
            case None       => ()
          }
          override val directory = true
          override val multiple = false
          override val recursive = true
          override val title = dialogTitle
        }
        dialog
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
