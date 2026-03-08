package marubinotto.router

final class Arg[A] private[router] ()

object Arg {
  def apply[A](): Arg[A] = new Arg[A]()
}
