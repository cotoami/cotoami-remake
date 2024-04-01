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

  case class Model(folded: Boolean = false, form: Form = CotoForm())

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
            material_symbol("text_snippet"),
            "Coto"
          ),
          button(className := "new-cotonoma default", disabled := false)(
            material_symbol("topic"),
            "Cotonoma"
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
        initialPrimarySize = 300,
        resizable = !model.folded,
        className = None,
        onPrimarySizeChanged = (
            (newSize) => println(s"newSize: ${newSize}")
        )
      )(
        SplitPane.Primary(className = Some("coto-editor"))(
          textarea(placeholder := "Write your Coto in Markdown here")()
        ),
        SplitPane.Secondary(className = None)(
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
        )
      )
    )
}
