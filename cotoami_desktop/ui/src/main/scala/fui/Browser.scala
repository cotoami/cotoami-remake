package fui

import scala.util.{Failure, Success}
import scala.concurrent._
import scala.scalajs.js
import scala.scalajs.js.JSStringOps._
import org.scalajs.dom
import org.scalajs.dom.{Element, URL}

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

  def send[Msg](msg: Msg): Cmd.One[Msg] = Cmd(IO(Some(msg)))

  def debounce[T](func: T => Unit, delay: Double): T => Unit = {
    var timeoutId: Option[Int] = None
    (t: T) => {
      timeoutId.foreach(dom.window.clearTimeout)
      timeoutId = Some(dom.window.setTimeout(() => func(t), delay))
    }
  }

  def debounce[T1, T2](
      func: (T1, T2) => Unit,
      delay: Double
  ): (T1, T2) => Unit = {
    var timeoutId: Option[Int] = None
    (t1: T1, t2: T2) => {
      timeoutId.foreach(dom.window.clearTimeout)
      timeoutId = Some(dom.window.setTimeout(() => func(t1, t2), delay))
    }
  }

  /** Change the URL, but do not trigger a page load. This will add a new entry
    * to the browser history.
    *
    * @param notify
    *   whether or not to notify the url change via `onUrlChange`
    */
  def pushUrl[Msg](url: String, notify: Boolean = true): Cmd.One[Msg] =
    Cmd(IO {
      dom.window.history.pushState((), "", url)
      if (notify) {
        listenersOnPushUrl.foreach(_(new URL(dom.window.location.href)))
      }
      None
    })

  /** Change the URL, but do not trigger a page load. This will not add a new
    * entry to the browser history.
    */
  def replaceUrl[Msg](url: String): Cmd.One[Msg] =
    Cmd(IO {
      dom.window.history.replaceState((), "", url)
      None
    })

  def reload[Msg](): Cmd.One[Msg] =
    Cmd(IO {
      dom.window.location.reload()
      None
    })

  def ajaxGetJson(url: String): Cmd.One[Either[Throwable, Json]] =
    Cmd(IO.async { cb =>
      IO {
        dom.fetch(url).flatMap(_.text()).onComplete {
          // Returning a Right even when the process has failed so that
          // the error can be handled as a Msg.
          case Success(text) => {
            val parseResult = parse(text)
            cb(Right(Some(parseResult)))
          }
          case Failure(t) => {
            cb(Right(Some(Left(t))))
          }
        }
        None // no finalizer on cancellation
      }
    })

  def ajaxGet[Result](
      url: String,
      decoder: Decoder[Result]
  ): Cmd.One[Either[Throwable, Result]] =
    Cmd(IO.async { cb =>
      IO {
        implicit val resultDecoder = decoder
        dom.fetch(url).flatMap(_.text()).onComplete {
          // Returning a Right even when the process has failed so that
          // the error can be handled as a Msg.
          case Success(text) => {
            val decoded = decode[Result](text)
            cb(Right(Some(decoded)))
          }
          case Failure(t) => {
            cb(Right(Some(Left(t))))
          }
        }
        None // no finalizer on cancellation
      }
    })

  def setHtmlTheme[Msg](theme: String): Cmd.One[Msg] =
    Cmd(IO {
      dom.window.document.documentElement.setAttribute("data-theme", theme)
      None
    })

  def encodeAsBase64(
      blob: dom.Blob,
      removePadding: Boolean = false
  ): Cmd.One[Either[dom.ProgressEvent, String]] =
    Cmd(IO.async { cb =>
      IO {
        val reader = new dom.FileReader()
        reader.onload = _ => {
          val url = reader.result.asInstanceOf[String]
          val base64 = url.substring(url.indexOf(',') + 1)
          if (removePadding) {
            val base64WithoutPadding = base64.replaceAll("=+$", "")
            cb(Right(Some(Right(base64WithoutPadding))))
          } else {
            cb(Right(Some(Right(base64))))
          }
        }
        reader.onerror = (e: dom.ProgressEvent) => {
          cb(Right(Some(Left(e))))
        }
        reader.readAsDataURL(blob)

        None // no finalizer on cancellation
      }
    })

  def decodeBase64(base64: String, contentType: String): dom.Blob = {
    val byteArray = js.typedarray.Uint8Array.from(
      // Window: atob()
      // decodes a string of data which has been encoded using Base64 encoding.
      // The result value is an ASCII string containing decoded data.
      // https://developer.mozilla.org/en-US/docs/Web/API/Window/atob
      //
      // String.split() in JavaScript (JSStringOps.jsSplit in Scala.js)
      // When the empty string ("") is used as a separator, the string is not split by
      // user-perceived characters (grapheme clusters) or unicode characters (code points),
      // but by UTF-16 code units. This destroys surrogate pairs.
      // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/split
      dom.window.atob(base64).jsSplit("").map(_.charAt(0).toShort)
    )
    new dom.Blob(
      js.Array(byteArray),
      new dom.BlobPropertyBag { `type` = contentType }
    )
  }

  def getCurrentPosition: Cmd.One[Either[dom.PositionError, dom.Position]] =
    Cmd(IO.async { cb =>
      IO {
        dom.window.navigator.geolocation.getCurrentPosition(
          position => cb(Right(Some(Right(position)))),
          error => cb(Right(Some(Left(error))))
        )
        None
      }
    })

  def createImage(url: String): Future[dom.HTMLImageElement] = {
    val promise = Promise[dom.HTMLImageElement]()

    val image = new dom.Image()
    image.onload = _ => promise.success(image)
    image.addEventListener(
      // https://developer.mozilla.org/en-US/docs/Web/API/HTMLImageElement#errors
      "error",
      (_: dom.Event) =>
        promise.failure(
          new IllegalArgumentException(s"Couldn't load the image: ${url}")
        )
    )
    // needed to avoid cross-origin issues on CodeSandbox
    image.setAttribute("crossOrigin", "anonymous")
    image.src = url

    promise.future
  }
}
