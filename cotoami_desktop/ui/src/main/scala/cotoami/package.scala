package object cotoami {

  case class Model(
      uiState: UiState = UiState(),
      messages: Seq[String] = Seq.empty,
      input: String = ""
  )

  case class UiState(paneToggles: Map[String, Boolean] = Map()) {
    def paneOpened(name: String): Boolean =
      this.paneToggles.getOrElse(name, true)

    def togglePane(name: String): UiState =
      this.copy(paneToggles =
        this.paneToggles.updated(name, !this.paneOpened(name))
      )
  }

  sealed trait Msg
  case class TogglePane(name: String) extends Msg
  case class Input(input: String) extends Msg
  case object Send extends Msg

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }
}
