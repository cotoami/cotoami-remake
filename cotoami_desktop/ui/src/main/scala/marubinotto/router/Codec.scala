package marubinotto.router

import scala.util.Try

trait Codec[A] {
  def encode(value: A): Option[String]
  def decode(value: Option[String]): Option[A]
}

object Codec {
  given stringCodec: Codec[String] with {
    override def encode(value: String): Option[String] = Some(value)
    override def decode(value: Option[String]): Option[String] = value
  }

  given intCodec: Codec[Int] with {
    override def encode(value: Int): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Int] =
      value.flatMap(s => Try(s.toInt).toOption)
  }

  given longCodec: Codec[Long] with {
    override def encode(value: Long): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Long] =
      value.flatMap(s => Try(s.toLong).toOption)
  }

  given doubleCodec: Codec[Double] with {
    override def encode(value: Double): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Double] =
      value.flatMap(s => Try(s.toDouble).toOption)
  }

  given booleanCodec: Codec[Boolean] with {
    override def encode(value: Boolean): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Boolean] =
      value.flatMap {
        case "true"  => Some(true)
        case "false" => Some(false)
        case _       => None
      }
  }
}
