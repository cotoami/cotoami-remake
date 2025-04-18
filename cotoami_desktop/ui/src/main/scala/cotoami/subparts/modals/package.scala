package cotoami.subparts

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html
import slinky.web.html._
import slinky.web.SyntheticMouseEvent

import cotoami.Context
import marubinotto.{optionalClasses, Validation}
import marubinotto.components.toolButton

package object modals {

  def buttonEdit(
      onClick: SyntheticMouseEvent[_] => Unit
  )(implicit context: Context): ReactElement =
    toolButton(
      classes = "edit",
      symbol = "edit",
      tip = Some(context.i18n.text.Edit),
      onClick = onClick
    )

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

  def fieldEditable(
      name: String,
      classes: String = "",
      edit: FieldEdit
  )(fieldContent: ReactElement*)(implicit
      context: Context
  ): ReactElement =
    div(className := s"field ${classes}")(
      section(className := "field-name")(name),
      section(
        className := optionalClasses(
          Seq(
            ("field-content", true),
            ("editing", edit.editing)
          )
        )
      )(
        div(className := "content")(fieldContent: _*),
        viewFieldEdit(edit)
      )
    )

  case class FieldEdit(
      disabled: Boolean = false,
      onEditClick: SyntheticMouseEvent[_] => Unit,
      onSaveClick: SyntheticMouseEvent[_] => Unit = _ => (),
      onCancelClick: SyntheticMouseEvent[_] => Unit = _ => (),
      editing: Boolean = false,
      validated: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  )

  private def viewFieldEdit(
      model: FieldEdit
  )(implicit context: Context): ReactElement =
    Option.when(!model.disabled) {
      Fragment(
        div(className := "edit")(
          if (model.saving)
            span(
              className := "processing",
              aria - "busy" := model.saving.toString()
            )()
          else if (model.editing)
            Fragment(
              toolButton(
                classes = "save",
                symbol = "database_upload",
                tip = Some(context.i18n.text.Save),
                disabled = model.saving || !model.validated,
                onClick = model.onSaveClick
              ),
              toolButton(
                classes = "cancel",
                symbol = "close",
                tip = Some(context.i18n.text.Cancel),
                onClick = model.onCancelClick
              )
            )
          else
            buttonEdit(model.onEditClick)
        ),
        model.error.map(section(className := "error")(_))
      )
    }
}
