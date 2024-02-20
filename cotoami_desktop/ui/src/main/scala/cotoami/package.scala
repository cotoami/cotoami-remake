package object cotoami {

  case class Model(messages: Seq[String] = Seq.empty, input: String = "")

  sealed trait Msg
  case class Input(input: String) extends Msg
  case object Send extends Msg

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }
}
