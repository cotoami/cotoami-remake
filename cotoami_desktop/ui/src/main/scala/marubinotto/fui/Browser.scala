package marubinotto.fui

import scala.util.{Failure, Success}
import scala.concurrent.*
import scala.scalajs.js
import scala.scalajs.js.JSStringOps.*
import org.scalajs.dom
import org.scalajs.dom.{Element, URL}

import cats.effect.{IO, Resource}
import cats.syntax.functor.*

import io.circe.*
import io.circe.parser.*

object Browser {
  // https://github.com/scala-js/scala-js-macrotask-executor
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

  // https://www.scala-js.org/api/scalajs-library/1.12.0/scala/scalajs/js/Thenable$$Implicits$.html
  import js.Thenable.Implicits.*

  private var listenersOnPushUrl: List[URL => Unit] = Nil

  /** Run until canceled or the update loop fails, releasing all owned
    * resources.
    */
  def run[Model, Msg](
      container: Element,
      program: Program[Model, Msg],
      onError: RuntimeError => IO[Unit] = RuntimeError.log
  ): IO[Unit] =
    mount(container, program, onError).use(
      _.awaitTermination
    )

  /** Mount on acquisition and unmount on release. The caller owns the scope and
    * fatal-error monitoring; a loop failure does not interrupt use. The
    * returned Runtime exposes dispatch and awaitFailure.
    */
  def mount[Model, Msg](
      container: Element,
      program: Program[Model, Msg],
      onError: RuntimeError => IO[Unit] = RuntimeError.log
  ): Resource[IO, Runtime[Model, Msg]] =
    Runtime.make(container, program, onError).flatMap { runtime =>
      Resource.make(IO {
        val listener: URL => Unit = runtime.onPushUrl
        listenersOnPushUrl = listener :: listenersOnPushUrl
        listener
      })(listener =>
        IO {
          listenersOnPushUrl = listenersOnPushUrl.filterNot(_ eq listener)
        }
      ).as(runtime)
    }

  def send[Msg](msg: Msg): Cmd.One[Msg] = IO.pure(msg).toCmd

  /** Change the URL, but do not trigger a page load. This will add a new entry
    * to the browser history.
    *
    * @param notify
    *   whether or not to notify the url change via `onUrlChange`
    */
  def pushUrl(url: String, notify: Boolean = true): IO[Unit] =
    IO {
      dom.window.history.pushState((), "", url)
      if (notify) {
        listenersOnPushUrl.foreach(_(new URL(dom.window.location.href)))
      }
    }

  /** Change the URL, but do not trigger a page load. This will not add a new
    * entry to the browser history.
    */
  def replaceUrl(url: String): IO[Unit] =
    IO {
      dom.window.history.replaceState((), "", url)
    }

  def reload(): IO[Unit] =
    IO {
      dom.window.location.reload()
    }

  // The cancel token covers both the request and consumption of its body.
  private def fetchResult[A](url: String, requestMethod: dom.HttpMethod)(
      consume: dom.Response => Future[A]
  ): IO[Either[Throwable, A]] =
    IO.async { cb =>
      IO {
        val controller = new dom.AbortController()
        var canceled = false
        dom.fetch(
          url,
          new dom.RequestInit {
            method = requestMethod
            signal = controller.signal
          }
        ).onComplete {
          case Success(response) if !canceled =>
            scala.util.Try(consume(response)) match {
              case Success(result) =>
                result.onComplete { outcome =>
                  if (!canceled) cb(Right(outcome.toEither))
                }
              case Failure(error) => cb(Right(Left(error)))
            }
          case Failure(error) if !canceled => cb(Right(Left(error)))
          case _                           => ()
        }
        Some(IO {
          canceled = true
          controller.abort()
        })
      }
    }

  def ajaxGetJson(url: String): IO[Either[Throwable, Json]] =
    fetchResult(url, dom.HttpMethod.GET)(_.text().toFuture)
      .map(_.flatMap(parse(_)))

  def ajaxGet[Result](
      url: String,
      decoder: Decoder[Result]
  ): IO[Either[Throwable, Result]] =
    fetchResult(url, dom.HttpMethod.GET)(_.text().toFuture)
      .map(_.flatMap(text => decode[Result](text)(using decoder)))

  def ajaxHead(url: String): IO[Either[Throwable, dom.Response]] =
    fetchResult(url, dom.HttpMethod.HEAD)(Future.successful)

  def setHtmlTheme(theme: String): IO[Unit] =
    IO {
      dom.window.document.documentElement.setAttribute("data-theme", theme)
    }

  def setLocalStorage(key: String, value: String): IO[Unit] =
    IO {
      dom.window.localStorage.setItem(key, value)
    }

  def getLocalStorage(key: String): IO[Option[String]] =
    IO(Option(dom.window.localStorage.getItem(key)))

  def removeLocalStorage(key: String): IO[Unit] =
    IO {
      dom.window.localStorage.removeItem(key)
    }

  def encodeAsBase64(
      blob: dom.Blob,
      removePadding: Boolean = false
  ): IO[Either[dom.ProgressEvent, String]] =
    Resource.make(IO(new dom.FileReader()))(reader =>
      IO {
        reader.onload = null
        reader.onerror = null
      }
    ).use { reader =>
      IO.async { cb =>
        IO {
          var canceled = false
          reader.onload = _ =>
            if (!canceled) {
              val url = reader.result.asInstanceOf[String]
              val base64 = url.substring(url.indexOf(',') + 1)
              cb(
                Right(
                  Right(
                    if (removePadding) base64.replaceAll("=+$", "") else base64
                  )
                )
              )
            }
          reader.onerror =
            (e: dom.ProgressEvent) => if (!canceled) cb(Right(Left(e)))
          reader.readAsDataURL(blob)
          Some(IO {
            canceled = true
            reader.onload = null
            reader.onerror = null
            reader.abort()
          })
        }
      }
    }

  def decodeBase64(base64: String, contentType: String): IO[dom.Blob] = IO {
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

  def getCurrentPosition: IO[Either[dom.PositionError, dom.Position]] =
    IO.async { cb =>
      IO {
        dom.window.navigator.geolocation.getCurrentPosition(
          position => cb(Right(Right(position))),
          error => cb(Right(Left(error)))
        )
        None
      }
    }

  def createImage(url: String): IO[dom.HTMLImageElement] = IO.fromFuture(IO {
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
  })
}
