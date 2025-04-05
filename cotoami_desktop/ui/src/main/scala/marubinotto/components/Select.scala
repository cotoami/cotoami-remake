package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

import marubinotto.facade.Nullable

@react object Select extends ExternalComponent {
  case class Props(
      className: String,
      options: Seq[SelectOption],
      placeholder: Option[String] = None,
      inputValue: String = "",
      value: SelectOption = null,
      onInputChange: Option[String => Unit] = None,
      formatOptionLabel: Option[SelectOption => ReactElement] = None,
      isLoading: Boolean = false,
      noOptionsMessage: Option[NoOptionsMessageArg => ReactElement] = None,
      isClearable: Boolean = false,
      autoFocus: Boolean = false,
      menuPlacement: String = "auto", // "auto" / "bottom" / "top"
      onChange: Option[Nullable[SelectOption] => Unit] = None
  )

  trait SelectOption extends js.Object {
    val value: String
    val label: String
    val isDisabled: Boolean
  }

  @js.native
  trait NoOptionsMessageArg extends js.Object {
    val inputValue: String = js.native
  }

  override val component = ReactSelect
}

@js.native
@JSImport("react-select", JSImport.Default)
object ReactSelect extends js.Object
