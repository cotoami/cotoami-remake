package marubinotto.router

import scala.util.Try

trait Codec[A] {
  def encode(value: A): Option[String]
  def decode(value: Option[String]): Option[A]
}

object Codec {
  implicit val stringCodec: Codec[String] = new Codec[String] {
    override def encode(value: String): Option[String] = Some(value)
    override def decode(value: Option[String]): Option[String] = value
  }

  implicit val intCodec: Codec[Int] = new Codec[Int] {
    override def encode(value: Int): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Int] =
      value.flatMap(s => Try(s.toInt).toOption)
  }

  implicit val longCodec: Codec[Long] = new Codec[Long] {
    override def encode(value: Long): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Long] =
      value.flatMap(s => Try(s.toLong).toOption)
  }

  implicit val doubleCodec: Codec[Double] = new Codec[Double] {
    override def encode(value: Double): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Double] =
      value.flatMap(s => Try(s.toDouble).toOption)
  }

  implicit val booleanCodec: Codec[Boolean] = new Codec[Boolean] {
    override def encode(value: Boolean): Option[String] = Some(value.toString)
    override def decode(value: Option[String]): Option[Boolean] =
      value.flatMap {
        case "true"  => Some(true)
        case "false" => Some(false)
        case _       => None
      }
  }
}
