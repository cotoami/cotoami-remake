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

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    msg match {
      case SetCotoForm =>
        (model.copy(form = CotoForm()), Seq.empty)

      case SetCotonomaForm =>
        (model.copy(form = CotonomaForm()), Seq.empty)
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
                value := content
              )()
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
              value := cotonomaName
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
