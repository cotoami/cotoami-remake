package marubinotto.router

trait Appender[A, B, Out] {
  def append(left: A, right: B): Out
  def split(out: Out): (A, B)
}

private[router] trait LowPriorityAppender {
  given singleAppender[A, B]: Appender[A, B, (A, B)] with {
      override def append(left: A, right: B): (A, B) = (left, right)
      override def split(out: (A, B)): (A, B) = out
    }
}

object Appender extends LowPriorityAppender {
  given unitAppender[B]: Appender[Unit, B, B] with {
      override def append(left: Unit, right: B): B = right
      override def split(out: B): (Unit, B) = ((), out)
    }

  given emptyTupleAppender[B]: Appender[EmptyTuple, B, Tuple1[B]] with {
    override def append(left: EmptyTuple, right: B): Tuple1[B] = Tuple1(right)
    override def split(out: Tuple1[B]): (EmptyTuple, B) = (EmptyTuple, out._1)
  }

  given tupleAppender[H, T <: Tuple, B, Out <: Tuple](using
      tail: Appender[T, B, Out]
  ): Appender[H *: T, B, H *: Out] with {
    override def append(left: H *: T, right: B): H *: Out =
      left.head *: tail.append(left.tail, right)

    override def split(out: H *: Out): (H *: T, B) = {
      val (init, last) = tail.split(out.tail)
      (out.head *: init, last)
    }
  }
}
