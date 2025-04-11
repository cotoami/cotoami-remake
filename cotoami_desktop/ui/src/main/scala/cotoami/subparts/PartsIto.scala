package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.Validation
import marubinotto.components.toolButton

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.Ito

object PartsIto {

  def buttonPin(
      ito: Ito
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val canEditPin = context.repo.nodes.canEdit(ito)
    div(
      className := optionalClasses(
        Seq(
          ("ito-container", true),
          ("with-description", ito.description.isDefined)
        )
      )
    )(
      div(
        className := optionalClasses(
          Seq(
            ("pin", true),
            ("ito", true),
            ("editable", canEditPin)
          )
        )
      )(
        toolButton(
          classes = "edit-pin",
          symbol = "push_pin",
          tip = Option.when(canEditPin)(context.i18n.text.Ito_editPin),
          tipPlacement = "right",
          disabled = !canEditPin,
          onClick = e => {
            e.stopPropagation()
            dispatch(Modal.Msg.OpenModal(Modal.EditIto(ito)))
          }
        ),
        ito.description.map(phrase =>
          section(
            className := "description",
            onClick := (e => {
              e.stopPropagation()
              if (canEditPin)
                dispatch(Modal.Msg.OpenModal(Modal.EditIto(ito)))
            })
          )(phrase)
        )
      )
    )
  }

  def buttonSubcotoIto(
      ito: Ito
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val canEditIto = context.repo.nodes.canEdit(ito)
    div(
      className := optionalClasses(
        Seq(
          ("ito-container", true),
          ("with-description", ito.description.isDefined)
        )
      )
    )(
      div(
        className := optionalClasses(
          Seq(
            ("subcoto-ito", true),
            ("ito", true),
            ("editable", canEditIto)
          )
        )
      )(
        toolButton(
          classes = "edit-ito",
          symbol = "subdirectory_arrow_right",
          tip = Option.when(canEditIto)("Edit ito"),
          tipPlacement = "right",
          disabled = !canEditIto,
          onClick = e => {
            e.stopPropagation()
            dispatch(Modal.Msg.OpenModal(Modal.EditIto(ito)))
          }
        ),
        ito.description.map(phrase =>
          section(
            className := "description",
            onClick := (e => {
              e.stopPropagation()
              if (canEditIto)
                dispatch(Modal.Msg.OpenModal(Modal.EditIto(ito)))
            })
          )(phrase)
        )
      )
    )
  }

  def inputDescription(
      description: String,
      validation: Validation.Result,
      onChange: String => Unit
  )(implicit context: Context): ReactElement =
    div(className := "description")(
      input(
        className := "description",
        `type` := "text",
        placeholder := context.i18n.text.Ito_description_placeholder,
        value := description,
        Validation.ariaInvalid(validation),
        slinky.web.html.onChange := (e => onChange(e.target.value))
      ),
      Validation.sectionValidationError(validation)
    )
}
