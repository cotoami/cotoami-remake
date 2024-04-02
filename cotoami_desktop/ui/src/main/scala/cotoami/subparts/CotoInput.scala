package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.FunctionalUI._
import cotoami.backend.{Cotonoma, Node}
import cotoami.components.{
  material_symbol,
  node_img,
  optionalClasses,
  SplitPane
}

object CotoInput {

  case class Model(
      name: String,
      folded: Boolean = false,
      form: Form = CotoForm()
  )

  sealed trait Form
  case class CotoForm(content: String = "") extends Form
  case class CotonomaForm(name: String = "") extends Form

  sealed trait Msg
  case object SetCotoForm extends Msg
  case object SetCotonomaForm extends Msg
  case class CotoContentInput(content: String) extends Msg
  case class CotonomaNameInput(name: String) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    (msg, model.form) match {
      case (SetCotoForm, _) =>
        (model.copy(form = CotoForm()), Seq.empty)

      case (SetCotonomaForm, _) =>
        (model.copy(form = CotonomaForm()), Seq.empty)

      case (CotoContentInput(content), form: CotoForm) =>
        (model.copy(form = form.copy(content = content)), Seq.empty)

      case (CotonomaNameInput(name), form: CotonomaForm) =>
        (model.copy(form = form.copy(name = name)), Seq.empty)

      case (_, _) => (model, Seq.empty)
    }

  def view(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      editorHeight: Int,
      onEditorHeightChanged: Int => Unit,
      dispatch: Msg => Unit
  ): ReactElement =
    section(
      className := optionalClasses(
        Seq(
          ("coto-input", true),
          ("folded", model.folded)
        )
      )
    )(
      header(className := "tools")(
        section(className := "coto-type-switch")(
          button(
            className := "new-coto default",
            disabled := model.form.isInstanceOf[CotoForm],
            onClick := (_ => dispatch(SetCotoForm))
          )(
            span(className := "label")(
              material_symbol("text_snippet"),
              "Coto"
            )
          ),
          button(
            className := "new-cotonoma default",
            disabled := model.form.isInstanceOf[CotonomaForm],
            onClick := (_ => dispatch(SetCotonomaForm))
          )(
            span(className := "label")(
              material_symbol("topic"),
              "Cotonoma"
            )
          )
        ),
        button(
          className := "image default",
          data - "tooltip" := "Image",
          data - "placement" := "bottom",
          disabled := !model.form.isInstanceOf[CotoForm]
        )(
          material_symbol("image")
        ),
        button(
          className := "location default",
          data - "tooltip" := "Location",
          data - "placement" := "bottom"
        )(
          material_symbol("location_on")
        )
      ),
      model.form match {
        case CotoForm(content) =>
          SplitPane(
            vertical = false,
            initialPrimarySize = editorHeight,
            resizable = !model.folded,
            className = None,
            onPrimarySizeChanged = onEditorHeightChanged
          )(
            SplitPane.Primary(className = Some("coto-editor"))(
              textarea(
                placeholder := "Write your Coto in Markdown here",
                value := content,
                onChange := ((e) => dispatch(CotoContentInput(e.target.value)))
              )
            ),
            SplitPane.Secondary(className = None)(
              inputFooter(model, operatingNode, currentCotonoma, dispatch)
            )
          )

        case CotonomaForm(cotonomaName) =>
          div()(
            input(
              `type` := "text",
              name := "cotonomaName",
              placeholder := "New cotonoma name",
              value := cotonomaName,
              onChange := ((e) => dispatch(CotonomaNameInput(e.target.value)))
            ),
            inputFooter(model, operatingNode, currentCotonoma, dispatch)
          )
      }
    )

  def inputFooter(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    footer(className := "post")(
      address(className := "poster")(
        node_img(operatingNode),
        operatingNode.name
      ),
      button(className := "post", disabled := true)(
        s"Post to \"${currentCotonoma.name}\"",
        span(className := "shortcut-help")("(Ctrl + Enter)")
      )
    )
}
