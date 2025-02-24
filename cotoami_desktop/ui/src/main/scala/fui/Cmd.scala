package fui

import scala.util.{Failure, Success}
import scala.concurrent.Future
import cats.effect.IO
import cats.implicits._

sealed trait Cmd[+Msg] extends Any {
  def map[OtherMsg](f: Msg => OtherMsg): Cmd[OtherMsg]
  def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg]
}

object Cmd {
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

  def apply[Msg](io: IO[Option[Msg]]): One[Msg] = One(io)

  case class Debounce(key: String, delay: Double)

  case class One[+Msg](io: IO[Option[Msg]], debounce: Option[Debounce] = None)
      extends Cmd[Msg] {
    override def map[OtherMsg](f: Msg => OtherMsg): One[OtherMsg] =
      One(io.map(_.map(f)), debounce)

    def flatMap[OtherMsg](f: Msg => One[OtherMsg]): One[OtherMsg] =
      One(io.flatMap(_.map(f(_).io).getOrElse(IO.none)), debounce)

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case o: One[LubMsg]         => Batch(this, o)
        case Batch(cmds @ _*)       => Batch.fromSeq(this +: cmds)
        case Sequence(batches @ _*) => Sequence.fromSeq(toBatch +: batches)
      }

    def toBatch: Batch[Msg] = Batch(this)

    def debounce(key: String, delay: Double): One[Msg] =
      copy(debounce = Some(Debounce(key, delay)))
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
        case o: One[LubMsg]         => Batch.fromSeq(cmds :+ o)
        case Batch(cmds @ _*)       => Batch.fromSeq(this.cmds ++ cmds)
        case Sequence(batches @ _*) => Sequence.fromSeq(this +: batches)
      }

    def sequencedOne: Cmd.One[Seq[Msg]] =
      Cmd.One(cmds.map(_.io).sequence.map(msgs => Some(msgs.flatten)))
  }

  object Batch {
    def fromSeq[Msg](cmds: Seq[One[Msg]]): Batch[Msg] = Batch(cmds: _*)
  }

  case class Sequence[+Msg](batches: Batch[Msg]*) extends Cmd[Msg] {
    override def map[OtherMsg](f: Msg => OtherMsg): Sequence[OtherMsg] =
      Sequence(batches.map(_.map(f)): _*)

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case o: One[LubMsg]         => Sequence.fromSeq(batches :+ o.toBatch)
        case b: Batch[LubMsg]       => Sequence.fromSeq(batches :+ b)
        case Sequence(batches @ _*) => Sequence.fromSeq(this.batches ++ batches)
      }
  }

  object Sequence {
    def fromSeq[Msg](batches: Seq[Batch[Msg]]): Sequence[Msg] =
      Sequence(batches: _*)
  }

  def none[Msg]: One[Msg] = One(IO.none)

  def fromFuture[T](future: Future[T]): One[Either[Throwable, T]] =
    One(IO.async { cb =>
      IO {
        future.onComplete {
          case Success(value) => cb(Right(Some(Right(value))))
          case Failure(t)     => cb(Right(Some(Left(t))))
        }
        None
      }
    })
}
