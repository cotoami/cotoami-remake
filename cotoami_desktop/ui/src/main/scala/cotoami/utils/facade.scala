package cotoami.utils

import scala.scalajs.js.|

object facade {

  type Nullable[T] = T | Null

  object Nullable {
    def unwrap[T](value: Nullable[T]): T = value.asInstanceOf[T]

    def toOption[T](value: Nullable[T]): Option[T] =
      Option(value.asInstanceOf[T])
  }
}
