package fui

import scala.util.Success
import scala.util.Failure
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL

import cats.effect.IO

import io.circe._
import io.circe.Decoder
import io.circe.parser._

object Browser {
  // https://github.com/scala-js/scala-js-macrotask-executor
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

  // https://www.scala-js.org/api/scalajs-library/1.12.0/scala/scalajs/js/Thenable$$Implicits$.html
  import js.Thenable.Implicits._

  private var listenersOnPushUrl: List[URL => Unit] = Nil

  def runProgram[Model, Msg](
      container: Element,
      program: Program[Model, Msg]
  ) = {
    val runtime = new Runtime(container, program)
    listenersOnPushUrl = runtime.onPushUrl _ :: listenersOnPushUrl
  }

  def send[Msg](msg: Msg): Cmd[Msg] = Cmd(IO(Some(msg)))

  /** Change the URL, but do not trigger a page load. This will add a new entry
    * to the browser history.
    */
  def pushUrl[Msg](url: String): Cmd[Msg] =
    Cmd(IO {
      dom.window.history.pushState((), "", url)
      listenersOnPushUrl.foreach(_(new URL(dom.window.location.href)))
      None
    })

  /** Change the URL, but do not trigger a page load. This will not add a new
    * entry to the browser history.
    */
  def replaceUrl[Msg](url: String): Cmd[Msg] =
    Cmd(IO {
      dom.window.history.replaceState((), "", url)
      None
    })

  def reload[Msg](): Cmd[Msg] =
    Cmd(IO {
      dom.window.location.reload()
      None
    })

  def ajaxGetJson[Msg](
      url: String,
      createMsg: Either[Throwable, Json] => Msg
  ): Cmd[Msg] =
    Cmd(IO.async { cb =>
      IO {
        dom.fetch(url).flatMap(_.text()).onComplete {
          // Returning a Right even when the process has failed so that
          // the error can be handled as a Msg.
          case Success(text) => {
            val parseResult = parse(text)
            cb(Right(Some(createMsg(parseResult))))
          }
          case Failure(t) => {
            cb(Right(Some(createMsg(Left(t)))))
          }
        }
        None // no finalizer on cancellation
      }
    })

  def ajaxGet[Msg, Result](
      url: String,
      decoder: Decoder[Result],
      createMsg: Either[Throwable, Result] => Msg
  ): Cmd[Msg] =
    Cmd(IO.async { cb =>
      IO {
        implicit val resultDecoder = decoder
        dom.fetch(url).flatMap(_.text()).onComplete {
          // Returning a Right even when the process has failed so that
          // the error can be handled as a Msg.
          case Success(text) => {
            val decoded = decode[Result](text)
            cb(Right(Some(createMsg(decoded))))
          }
          case Failure(t) => {
            cb(Right(Some(createMsg(Left(t)))))
          }
        }
        None // no finalizer on cancellation
      }
    })
}
