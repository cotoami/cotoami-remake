package cotoami.subparts

import scala.scalajs.js

import slinky.core.facade.ReactElement

import marubinotto.facade.Nullable
import marubinotto.components.Select

object SelectCotonoma {
  def apply(
      className: String = "",
      options: Seq[Select.SelectOption],
      placeholder: Option[String] = None,
      inputValue: js.UndefOr[String] = js.undefined,
      defaultValue: Option[Select.SelectOption] = None,
      value: Option[Select.SelectOption] = None,
      onInputChange: Option[(String, Select.InputActionMeta) => String] = None,
      formatOptionLabel: Option[Select.SelectOption => ReactElement] = None,
      isLoading: Boolean = false,
      noOptionsMessage: Option[Select.NoOptionsMessageArg => ReactElement] =
        None,
      isClearable: Boolean = false,
      autoFocus: Boolean = false,
      menuPlacement: String = "auto",
      onChange: Option[(Option[Select.SelectOption], Select.ActionMeta) => Unit] =
        None
  ): ReactElement =
    Select(
      className = Seq("cotonoma-select", className).filter(_.nonEmpty).mkString(" "),
      options = options,
      placeholder = placeholder,
      inputValue = inputValue,
      defaultValue = defaultValue,
      value = value,
      onInputChange = onInputChange,
      formatOptionLabel = formatOptionLabel,
      isLoading = isLoading,
      noOptionsMessage = noOptionsMessage,
      isClearable = isClearable,
      autoFocus = autoFocus,
      menuPlacement = menuPlacement,
      onChange = onChange.map(callback =>
        (option, actionMeta) => callback(Nullable.toOption(option), actionMeta)
      )
    )
}
