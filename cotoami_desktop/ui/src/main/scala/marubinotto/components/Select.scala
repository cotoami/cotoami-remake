package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.facade.ReactElement

import marubinotto.facade.Nullable

object Select extends ExternalComponent {
  case class Props(
      className: String,
      options: Seq[SelectOption],
      placeholder: Option[String] = None,
      inputValue: js.UndefOr[String] = js.undefined,
      defaultValue: Option[SelectOption] = None,
      value: Option[SelectOption] = None,
      onInputChange: Option[(String, InputActionMeta) => String] = None,
      formatOptionLabel: Option[SelectOption => ReactElement] = None,
      isLoading: Boolean = false,
      noOptionsMessage: Option[NoOptionsMessageArg => ReactElement] = None,
      isClearable: Boolean = false,
      autoFocus: Boolean = false,
      menuPlacement: String = "auto", // "auto" / "bottom" / "top"
      onChange: Option[(Nullable[SelectOption], ActionMeta) => Unit] = None
  )

  def apply(
      className: String,
      options: Seq[SelectOption],
      placeholder: Option[String] = None,
      inputValue: js.UndefOr[String] = js.undefined,
      defaultValue: Option[SelectOption] = None,
      value: Option[SelectOption] = None,
      onInputChange: Option[(String, InputActionMeta) => String] = None,
      formatOptionLabel: Option[SelectOption => ReactElement] = None,
      isLoading: Boolean = false,
      noOptionsMessage: Option[NoOptionsMessageArg => ReactElement] = None,
      isClearable: Boolean = false,
      autoFocus: Boolean = false,
      menuPlacement: String = "auto",
      onChange: Option[(Nullable[SelectOption], ActionMeta) => Unit] = None
  ) =
    super.apply(
      Props(
        className,
        options,
        placeholder,
        inputValue,
        defaultValue,
        value,
        onInputChange,
        formatOptionLabel,
        isLoading,
        noOptionsMessage,
        isClearable,
        autoFocus,
        menuPlacement,
        onChange
      )
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

  @js.native
  trait InputActionMeta extends js.Object {
    val action: String = js.native
    val prevInputValue: String = js.native
  }

  @js.native
  trait ActionMeta extends js.Object {
    val action: String = js.native
  }

  override val component = ReactSelect
}

@js.native
@JSImport("react-select", JSImport.Default)
object ReactSelect extends js.Object
