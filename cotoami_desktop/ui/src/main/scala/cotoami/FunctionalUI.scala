package cotoami

import scala.util.Success
import scala.util.Failure
import scala.collection.mutable.{Map => MutableMap}

import cats.effect.IO

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL
import org.scalajs.dom.raw.CustomEvent
import org.scalajs.dom.Event
import org.scalajs.dom.ext.Ajax

import slinky.core.facade.ReactElement
import slinky.web.ReactDOM

import io.circe._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

object FunctionalUI {
  type Cmd[Msg] = IO[Option[Msg]]
  type Cmds[Msg] = Seq[Cmd[Msg]]

  type Sub[Msg] = (Dispatch[Msg], OnSubscribe) => Unit
  type Subs[Msg] = Map[String, Sub[Msg]]
  type Dispatch[Msg] = Msg => Unit
  type OnSubscribe = Option[Unsubscribe] => Unit
  type Unsubscribe = () => Unit

  case class Program[Model, Msg](
      init: (URL) => (Model, Cmds[Msg]),
      view: (Model, Msg => Unit) => ReactElement,
      update: (Msg, Model) => (Model, Cmds[Msg]),
      subscriptions: Model => Subs[Msg] = (model: Model) =>
        Map.empty[String, Sub[Msg]],
      onUrlChange: Option[URL => Msg] = None
  )

  class Runtime[Model, Msg](
      container: Element,
      val program: Program[Model, Msg]
  ) {
    private val init = program.init(new URL(dom.window.location.href))
    private var state = init._1
    private val subs: MutableMap[String, Option[Unsubscribe]] = MutableMap()

    def dispatch(msg: Msg): Unit = {
      apply(program.update(msg, state))
    }

    def apply(change: (Model, Cmds[Msg])): Unit = {
      import cats.effect.unsafe.implicits.global

      val (model, cmds) = change
      state = model

      updateSubs(state)

      ReactDOM.render(program.view(model, dispatch), container)

      for (cmd <- cmds) {
        cmd.unsafeRunAsync {
          case Right(optionMsg) => optionMsg.map(dispatch(_))
          case Left(e) => throw e // IO should return Right even when it fails
        }
      }
    }

    def updateSubs(model: Model): Unit = {
      val newSubs = program.subscriptions(model)
      val keysToAdd = newSubs.keySet.diff(subs.keySet)
      val keysToRemove = subs.keySet.diff(newSubs.keySet)
      keysToAdd.foreach(key => {
        // subscribe and hold `unsubscribe` function
        newSubs.get(key) match {
          case Some(sub) => sub(dispatch, subs(key) = _)
          case None      => subs(key) = None
        }
      })
      keysToRemove.foreach(key => {
        // remove and unsubscribe
        subs.remove(key).map(_.map(_()))
      })
    }

    def onPushUrl(url: URL): Unit =
      program.onUrlChange.map(_(url)).map(dispatch(_))

    program.onUrlChange.map(onUrlChange => {
      dom.window.addEventListener(
        "popstate",
        (e: Event) => dispatch(onUrlChange(new URL(dom.window.location.href)))
      )
    })

    apply(init)
  }

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

    /** Change the URL, but do not trigger a page load. This will add a new
      * entry to the browser history.
      */
    def pushUrl[Msg](url: String): Cmd[Msg] =
      IO {
        dom.window.history.pushState((), "", url)
        listenersOnPushUrl.foreach(_(new URL(dom.window.location.href)))
        None
      }

    /** Change the URL, but do not trigger a page load. This will not add a new
      * entry to the browser history.
      */
    def replaceUrl[Msg](url: String): Cmd[Msg] =
      IO {
        dom.window.history.replaceState((), "", url)
        None
      }

    def ajaxGetJson[Msg](
        url: String,
        createMsg: Either[Throwable, Json] => Msg
    ): Cmd[Msg] =
      IO.async { cb =>
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
      }

    def ajaxGet[Msg, Result](
        url: String,
        decoder: Decoder[Result],
        createMsg: Either[Throwable, Result] => Msg
    ): Cmd[Msg] =
      IO.async { cb =>
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
      }
  }
}
