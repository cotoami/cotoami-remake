package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@react object Select extends ExternalComponent {
  case class Props(
      className: String,
      options: Seq[SelectOption],
      placeholder: Option[String] = None,
      formatOptionLabel: Option[SelectOption => ReactElement] = None,
      isLoading: Boolean = false
  )

  trait SelectOption extends js.Object {
    val value: String
    val label: String
  }

  override val component = ReactSelect
}

@js.native
@JSImport("react-select", JSImport.Default)
object ReactSelect extends js.Object
