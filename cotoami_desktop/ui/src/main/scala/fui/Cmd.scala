package fui

import scala.util.{Failure, Success}
import scala.concurrent.Future
import cats.effect.IO

sealed trait Cmd[+Msg] {
  def map[OtherMsg](f: Msg => OtherMsg): Cmd[OtherMsg]
  def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg]
}

object Cmd {
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

  def apply[Msg](io: IO[Option[Msg]]): Single[Msg] = Single(io)

  case class Single[+Msg](io: IO[Option[Msg]]) extends Cmd[Msg] {
    override def map[OtherMsg](f: Msg => OtherMsg): Single[OtherMsg] = Single(
      io.map(_.map(f))
    )

    def flatMap[OtherMsg](f: Msg => Single[OtherMsg]): Single[OtherMsg] =
      Single(io.flatMap(_.map(f(_).io).getOrElse(IO.none)))

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case s: Single[LubMsg]      => Batch(this, s)
        case Batch(cmds @ _*)       => Batch.fromSeq(this +: cmds)
        case Sequence(batches @ _*) => Sequence.fromSeq(Batch(this) +: batches)
      }
  }

  case class Batch[+Msg](cmds: Single[Msg]*) extends Cmd[Msg] {
    def :+[LubMsg >: Msg](single: Single[LubMsg]): Batch[LubMsg] =
      Batch.fromSeq(cmds :+ single)
    def +:[LubMsg >: Msg](single: Single[LubMsg]): Batch[LubMsg] =
      Batch.fromSeq(single +: cmds)

    override def map[OtherMsg](f: Msg => OtherMsg): Batch[OtherMsg] =
      Batch.fromSeq(cmds.map(_.map(f)))

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case s: Single[LubMsg]      => Batch.fromSeq(cmds :+ s)
        case Batch(cmds @ _*)       => Batch.fromSeq(this.cmds ++ cmds)
        case Sequence(batches @ _*) => Sequence.fromSeq(this +: batches)
      }
  }

  object Batch {
    def fromSeq[Msg](cmds: Seq[Single[Msg]]): Batch[Msg] = Batch(cmds: _*)
  }

  case class Sequence[+Msg](batches: Batch[Msg]*) extends Cmd[Msg] {
    override def map[OtherMsg](f: Msg => OtherMsg): Sequence[OtherMsg] =
      Sequence(batches.map(_.map(f)): _*)

    override def ++[LubMsg >: Msg](that: Cmd[LubMsg]): Cmd[LubMsg] =
      that match {
        case s: Single[LubMsg]      => Sequence.fromSeq(batches :+ Batch(s))
        case b: Batch[LubMsg]       => Sequence.fromSeq(batches :+ b)
        case Sequence(batches @ _*) => Sequence.fromSeq(this.batches ++ batches)
      }
  }

  object Sequence {
    def fromSeq[Msg](batches: Seq[Batch[Msg]]): Sequence[Msg] =
      Sequence(batches: _*)
  }

  def none[Msg]: Single[Msg] = Single(IO.none)

  def fromFuture[T](future: Future[T]): Single[Either[Throwable, T]] =
    Single(IO.async { cb =>
      IO {
        future.onComplete {
          case Success(value) => cb(Right(Some(Right(value))))
          case Failure(t)     => cb(Right(Some(Left(t))))
        }
        None
      }
    })
}
