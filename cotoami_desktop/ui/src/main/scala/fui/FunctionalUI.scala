package fui

import scala.util.Success
import scala.util.Failure
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration.FiniteDuration

import cats.MonoidK
import cats.effect.IO

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.URL
import org.scalajs.dom.EventTarget
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
  case class Program[Model, Msg](
      init: (URL) => (Model, Seq[Cmd[Msg]]),
      view: (Model, Msg => Unit) => ReactElement,
      update: (Msg, Model) => (Model, Seq[Cmd[Msg]]),
      subscriptions: Model => Sub[Msg] = (model: Model) => Sub.Empty,
      onUrlChange: Option[URL => Msg] = None
  )

  case class Cmd[Msg](io: IO[Option[Msg]]) extends AnyVal {
    def map[OtherMsg](f: Msg => OtherMsg): Cmd[OtherMsg] = Cmd(
      this.io.map(_.map(f(_)))
    )
  }

  sealed trait Sub[+Msg] {
    def map[OtherMsg](f: Msg => OtherMsg): Sub[OtherMsg]

    final def combine[LubMsg >: Msg](other: Sub[LubMsg]): Sub[LubMsg] =
      (this, other) match {
        case (Sub.Empty, Sub.Empty) => Sub.Empty
        case (Sub.Empty, s2)        => s2
        case (s1, Sub.Empty)        => s1
        case (s1, s2)               => Sub.Combined(s1, s2)
      }
  }

  implicit object MonoidKSub extends MonoidK[Sub] {
    def empty[Msg]: Sub[Msg] = Sub.Empty
    def combineK[Msg](sub1: Sub[Msg], sub2: Sub[Msg]): Sub[Msg] =
      sub1.combine(sub2)
  }

  object Sub {
    type Subscribe[Msg] = (Msg => Unit, OnSubscribe) => Unit
    type OnSubscribe = Option[Unsubscribe] => Unit
    type Unsubscribe = () => Unit

    case object Empty extends Sub[Nothing] {
      def map[OtherMsg](f: Nothing => OtherMsg): Sub[OtherMsg] = this
    }

    case class Combined[+Msg](sub1: Sub[Msg], sub2: Sub[Msg]) extends Sub[Msg] {
      def map[OtherMsg](f: Msg => OtherMsg): Sub[OtherMsg] =
        Combined(sub1.map(f), sub2.map(f))
    }

    case class Impl[Msg](
        id: String,
        subscribe: Subscribe[Msg]
    ) extends Sub[Msg] {
      def map[OtherMsg](f: Msg => OtherMsg): Sub[OtherMsg] =
        Impl(
          id,
          (dispatch: OtherMsg => Unit, onSubscribe) =>
            subscribe(msg => dispatch(f(msg)), onSubscribe)
        )
    }

    final def toMap[Msg](sub: Sub[Msg]): Map[String, Subscribe[Msg]] = {
      def collect(sub: Sub[Msg]): List[(String, Subscribe[Msg])] =
        sub match {
          case Sub.Empty                => Nil
          case Sub.Combined(sub1, sub2) => collect(sub1) ++ collect(sub2)
          case Sub.Impl(id, subscribe)  => List((id, subscribe))
        }
      collect(sub).toMap
    }

    def timeout[Msg](duration: FiniteDuration, msg: Msg, id: String): Sub[Msg] =
      Impl[Msg](
        id,
        (dispatch, onSubscribe) => {
          val handle = js.timers.setTimeout(duration) {
            dispatch(msg)
          }
          val unsubscribe = () => js.timers.clearTimeout(handle)
          onSubscribe(Some(unsubscribe))
        }
      )

    def every(interval: FiniteDuration, id: String): Sub[Long] =
      Impl[Long](
        id,
        (dispatch, onSubscribe) => {
          val handle = js.timers.setInterval(interval) {
            dispatch(System.currentTimeMillis())
          }
          val unsubscribe = () => js.timers.clearInterval(handle)
          onSubscribe(Some(unsubscribe))
        }
      )

    def fromEvent[Event, Msg](name: String, target: EventTarget)(
        toMsg: Event => Option[Msg]
    ): Sub[Msg] =
      Impl[Msg](
        name + target.hashCode,
        (dispatch, onSubscribe) => {
          val listener: js.Function1[Event, _] =
            (e: Event) => toMsg(e).map(dispatch(_))
          target.addEventListener(name, listener)

          val unsubscribe = () => target.removeEventListener(name, listener)
          onSubscribe(Some(unsubscribe))
        }
      )
  }

  class Runtime[Model, Msg](
      container: Element,
      val program: Program[Model, Msg]
  ) {
    private val init = program.init(new URL(dom.window.location.href))
    private var state = init._1
    private val subs: MutableMap[String, Option[Sub.Unsubscribe]] = MutableMap()

    def dispatch(msg: Msg): Unit = {
      apply(program.update(msg, state))
    }

    def apply(change: (Model, Seq[Cmd[Msg]])): Unit = {
      import cats.effect.unsafe.implicits.global

      val (model, cmds) = change
      state = model

      ReactDOM.render(program.view(model, dispatch), container)

      // Run side effects
      for (cmd <- cmds) {
        cmd.io.unsafeRunAsync {
          case Right(optionMsg) => optionMsg.map(dispatch(_))
          case Left(e) => throw e // IO should return Right even when it fails
        }
      }
      updateSubs(state)
    }

    def updateSubs(model: Model): Unit = {
      val nextSubs = Sub.toMap(program.subscriptions(model))
      val keysToAdd = nextSubs.keySet.diff(subs.keySet)
      val keysToRemove = subs.keySet.diff(nextSubs.keySet)
      keysToAdd.foreach(key => {
        // Register the key first to avoid multiple subscriptions
        subs.update(key, None)

        // subscribe and keep the `unsubscribe` function
        nextSubs.get(key) match {
          case Some(subscribe) => subscribe(dispatch, subs.update(key, _))
          case None            => subs.update(key, None)
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
}
