package marubinotto.fui

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import cats.effect.IO

extension [A](io: IO[A]) {
  /** Convert a computation into a command that emits its result, including Unit or Option. */
  def toCmd: Cmd.One[A] = Cmd.optional(io.map(value => Some(value)))
}

sealed trait Cmd[+Msg] extends Any {
  def map[OtherMsg](f: Msg => OtherMsg): Cmd[OtherMsg]
  def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg]
}

object Cmd {
  def apply[Msg](io: IO[Option[Msg]]): One[Msg] = optional(io)

  /** Execute without emitting a message. Errors and cancellation are preserved. */
  def exec[A](io: IO[A]): One[Nothing] = One(io.map(_ => None))

  /** Emit only when the computation returns Some. */
  def optional[Msg](io: IO[Option[Msg]]): One[Msg] = One(io)

  case class Debounce(key: String, delay: FiniteDuration)

  case class One[+Msg](io: IO[Option[Msg]], debounce: Option[Debounce] = None)
      extends Cmd[Msg] {
    override def map[OtherMsg](f: Msg => OtherMsg): One[OtherMsg] =
      One(io.map(_.map(f)), debounce)

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case o: One[LubMsg]   => Batch(this, o)
        case Batch(cmds*)     => Batch.fromSeq(this +: cmds)
      }

    def toNone[OtherMsg]: One[OtherMsg] = One(io.map(_ => None), debounce)

    def debounce(key: String, delay: FiniteDuration): One[Msg] =
      copy(debounce = Some(Debounce(key, delay)))
  }

  object One {
    def pure[Msg](msg: Msg): One[Msg] = IO.pure(msg).toCmd
  }

  case class Batch[+Msg](cmds: One[Msg]*) extends Cmd[Msg] {
    def :+[LubMsg >: Msg](one: One[LubMsg]): Batch[LubMsg] =
      Batch.fromSeq(cmds :+ one)
    def +:[LubMsg >: Msg](one: One[LubMsg]): Batch[LubMsg] =
      Batch.fromSeq(one +: cmds)

    override def map[OtherMsg](f: Msg => OtherMsg): Batch[OtherMsg] =
      Batch.fromSeq(cmds.map(_.map(f)))

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case o: One[LubMsg]   => Batch.fromSeq(cmds :+ o)
        case Batch(cmds*)     => Batch.fromSeq(this.cmds ++ cmds)
      }
  }

  object Batch {
    def fromSeq[Msg](cmds: Seq[One[Msg]]): Batch[Msg] = Batch(cmds*)
  }

  def none[Msg]: Batch[Msg] = Batch()

  /** Evaluate the Future expression on each execution and emit failures as Left. */
  def fromFuture[A](future: => Future[A]): One[Either[Throwable, A]] =
    IO.fromFuture(IO(future)).attempt.toCmd
}
