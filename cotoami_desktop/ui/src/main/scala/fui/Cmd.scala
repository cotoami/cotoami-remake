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
        case s: Cmd.Single[LubMsg] => Cmd.Batch(this, s)
        case Cmd.Batch(cmds @ _*)  => Cmd.Batch.fromSeq(this +: cmds)
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
        case s: Cmd.Single[LubMsg] => Cmd.Batch.fromSeq(cmds :+ s)
        case Cmd.Batch(cmds @ _*)  => Cmd.Batch.fromSeq(this.cmds ++ cmds)
      }
  }

  object Batch {
    def fromSeq[Msg](cmds: Seq[Single[Msg]]): Batch[Msg] = Batch(cmds: _*)
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
