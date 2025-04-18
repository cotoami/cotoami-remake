package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import cotoami.Context
import marubinotto.Validation
import marubinotto.components.toolButton

package object modals {

  def field(
      name: String,
      classes: String = ""
  )(fieldContent: ReactElement*): ReactElement =
    div(className := s"field ${classes}")(
      section(className := "field-name")(name),
      section(className := "field-content")(
        fieldContent: _*
      )
    )

  def fieldInput(
      name: String,
      classes: String = "",
      inputType: String = "text",
      inputPlaceholder: Option[String] = None,
      inputValue: String,
      readOnly: Boolean = false,
      inputErrors: Option[Validation.Result] = None,
      onInput: (String => Unit) = (_ => ())
  ): ReactElement =
    field(
      name = name,
      classes = classes
    )(
      input(
        `type` := inputType,
        placeholder := inputPlaceholder,
        value := inputValue,
        html.readOnly := readOnly,
        Validation.ariaInvalid(
          inputErrors.getOrElse(Validation.Result.notYetValidated)
        ),
        // Use onChange instead of onInput to suppress the React 'use defaultValue' warning
        // (onChange is almost the same as onInput in React)
        onChange := (e => onInput(e.target.value))
      ),
      inputErrors.map(Validation.sectionValidationError)
    )

  def buttonEdit(
      onClick: SyntheticMouseEvent[_] => Unit
  )(implicit context: Context): ReactElement =
    toolButton(
      classes = "edit",
      symbol = "edit",
      tip = Some(context.i18n.text.Edit),
      onClick = onClick
    )
}
