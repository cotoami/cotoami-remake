package cotoami.subparts

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.backend.{Cotonoma, Node}
import cotoami.components.{
  material_symbol,
  node_img,
  optionalClasses,
  SplitPane
}

object CotoInput {
  val EditorDefaultHeight = 150

  case class Model(
      folded: Boolean = false,
      editorHeight: Int = EditorDefaultHeight,
      onEditorHeightChanged: Int => Unit = (height => ()),
      form: Form = CotoForm()
  )

  sealed trait Form
  case class CotoForm(content: String = "") extends Form
  case class CotonomaForm(name: String = "") extends Form

  def view(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: cotoami.Msg => Unit
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
          button(className := "new-coto default", disabled := true)(
            span(className := "label")(
              material_symbol("text_snippet"),
              "Coto"
            )
          ),
          button(className := "new-cotonoma default", disabled := false)(
            span(className := "label")(
              material_symbol("topic"),
              "Cotonoma"
            )
          )
        ),
        button(
          className := "image default",
          data - "tooltip" := "Image",
          data - "placement" := "bottom"
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
      SplitPane(
        vertical = false,
        initialPrimarySize = model.editorHeight,
        resizable = !model.folded,
        className = None,
        onPrimarySizeChanged = model.onEditorHeightChanged
      )(
        SplitPane.Primary(className = Some("coto-editor"))(
          textarea(placeholder := "Write your Coto in Markdown here")()
        ),
        SplitPane.Secondary(className = None)(
          inputFooter(model, operatingNode, currentCotonoma, dispatch)
        )
      )
    )

  def inputFooter(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: cotoami.Msg => Unit
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
