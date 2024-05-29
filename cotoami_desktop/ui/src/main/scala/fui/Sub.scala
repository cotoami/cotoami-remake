package fui

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import org.scalajs.dom.EventTarget

import cats.MonoidK

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

object Sub {
  type Subscribe[Msg] = (Msg => Unit, OnSubscribe) => Unit
  type OnSubscribe = Option[Unsubscribe] => Unit
  type Unsubscribe = () => Unit

  implicit object MonoidKSub extends MonoidK[Sub] {
    def empty[Msg]: Sub[Msg] = Sub.Empty
    def combineK[Msg](sub1: Sub[Msg], sub2: Sub[Msg]): Sub[Msg] =
      sub1.combine(sub2)
  }

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
          (e: Event) => toMsg(e).map(dispatch)
        target.addEventListener(name, listener)

        val unsubscribe = () => target.removeEventListener(name, listener)
        onSubscribe(Some(unsubscribe))
      }
    )
}
