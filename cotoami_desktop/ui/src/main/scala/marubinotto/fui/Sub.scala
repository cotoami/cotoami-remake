package marubinotto.fui

import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import org.scalajs.dom.EventTarget

import cats.MonoidK
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.std.Queue

import fs2.Stream

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
      stream: Stream[IO, Msg]
  ) extends Sub[Msg] {
    def map[OtherMsg](f: Msg => OtherMsg): Sub[OtherMsg] =
      Impl(id, stream.map(f))
  }

  final def toMap[Msg](sub: Sub[Msg]): Map[String, Stream[IO, Msg]] = {
    def collect(sub: Sub[Msg]): List[(String, Stream[IO, Msg])] =
      sub match {
        case Sub.Empty                => Nil
        case Sub.Combined(sub1, sub2) => collect(sub1) ++ collect(sub2)
        case Sub.Impl(id, stream)     => List((id, stream))
      }
    collect(sub).toMap
  }

  def fromCallback[Msg](
      id: String
  )(subscribe: (Msg => Unit) => IO[IO[Unit]]): Sub[Msg] =
    Impl(
      id,
      Stream.resource(Dispatcher.parallel[IO]).flatMap { dispatcher =>
        Stream.eval(Queue.unbounded[IO, Option[Msg]]).flatMap { queue =>
          val emit: Msg => Unit =
            msg => dispatcher.unsafeRunAndForget(queue.offer(Some(msg)))
          val release =
            subscribe(emit).handleErrorWith(error => queue.offer(None) *> IO.raiseError(error))

          Stream
            .bracket(release)(cleanup =>
              cleanup.guarantee(queue.offer(None))
            )
            .flatMap(_ => Stream.fromQueueNoneTerminated(queue))
        }
      }
    )

  def timeout[Msg](duration: FiniteDuration, msg: Msg, id: String): Sub[Msg] =
    Impl[Msg](id, Stream.sleep_[IO](duration) ++ Stream.emit(msg))

  def every(interval: FiniteDuration, id: String): Sub[Long] =
    Impl[Long](
      id,
      Stream.awakeEvery[IO](interval).map(_ => System.currentTimeMillis())
    )

  def fromEvent[Event, Msg](name: String, target: EventTarget)(
      toMsg: Event => Option[Msg]
  ): Sub[Msg] =
    fromCallback(name + target.hashCode) { dispatch =>
      IO {
        val listener: js.Function1[Event, ?] =
          (e: Event) => toMsg(e).map(dispatch)
        target.addEventListener(name, listener)
        IO(target.removeEventListener(name, listener))
      }
    }
}
