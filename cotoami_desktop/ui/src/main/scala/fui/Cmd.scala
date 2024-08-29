package fui

import scala.util.{Failure, Success}
import scala.concurrent.Future
import cats.effect.IO

case class Cmd[+Msg](io: IO[Option[Msg]]) extends AnyVal {
  def map[OtherMsg](f: Msg => OtherMsg): Cmd[OtherMsg] = Cmd(
    this.io.map(_.map(f))
  )

  def flatMap[OtherMsg](f: Msg => Cmd[OtherMsg]): Cmd[OtherMsg] = Cmd(
    this.io.flatMap(_.map(f(_).io).getOrElse(IO.none))
  )
}

object Cmd {
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

  def none[Msg]: Cmd[Msg] = Cmd(IO.none)

  def fromFuture[T](future: Future[T]): Cmd[Either[Throwable, T]] =
    Cmd(IO.async { cb =>
      IO {
        future.onComplete {
          case Success(value) => cb(Right(Some(Right(value))))
          case Failure(t)     => cb(Right(Some(Left(t))))
        }
        None
      }
    })
}
