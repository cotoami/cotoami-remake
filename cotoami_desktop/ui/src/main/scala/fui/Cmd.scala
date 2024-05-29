package fui

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
  def none[Msg]: Cmd[Msg] = Cmd(IO.none)
}
