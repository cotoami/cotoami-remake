package marubinotto.router

trait Appender[A, B, Out] {
  def append(left: A, right: B): Out
  def split(out: Out): (A, B)
}

private[router] trait LowPriorityAppender {
  implicit def singleAppender[A, B]: Appender[A, B, (A, B)] =
    new Appender[A, B, (A, B)] {
      override def append(left: A, right: B): (A, B) = (left, right)
      override def split(out: (A, B)): (A, B) = out
    }
}

object Appender extends LowPriorityAppender {
  implicit def unitAppender[B]: Appender[Unit, B, B] =
    new Appender[Unit, B, B] {
      override def append(left: Unit, right: B): B = right
      override def split(out: B): (Unit, B) = ((), out)
    }

  implicit def tuple2Appender[A1, A2, B]: Appender[(A1, A2), B, (A1, A2, B)] =
    new Appender[(A1, A2), B, (A1, A2, B)] {
      override def append(left: (A1, A2), right: B): (A1, A2, B) =
        (left._1, left._2, right)

      override def split(out: (A1, A2, B)): ((A1, A2), B) =
        ((out._1, out._2), out._3)
    }

  implicit def tuple3Appender[A1, A2, A3, B]
      : Appender[(A1, A2, A3), B, (A1, A2, A3, B)] =
    new Appender[(A1, A2, A3), B, (A1, A2, A3, B)] {
      override def append(
          left: (A1, A2, A3),
          right: B
      ): (A1, A2, A3, B) =
        (left._1, left._2, left._3, right)

      override def split(
          out: (A1, A2, A3, B)
      ): ((A1, A2, A3), B) =
        ((out._1, out._2, out._3), out._4)
    }

  implicit def tuple4Appender[A1, A2, A3, A4, B]
      : Appender[(A1, A2, A3, A4), B, (A1, A2, A3, A4, B)] =
    new Appender[(A1, A2, A3, A4), B, (A1, A2, A3, A4, B)] {
      override def append(
          left: (A1, A2, A3, A4),
          right: B
      ): (A1, A2, A3, A4, B) =
        (left._1, left._2, left._3, left._4, right)

      override def split(
          out: (A1, A2, A3, A4, B)
      ): ((A1, A2, A3, A4), B) =
        ((out._1, out._2, out._3, out._4), out._5)
    }
}
