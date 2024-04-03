package cotoami.subparts

import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import cats.effect.IO

import fui.FunctionalUI._
import cotoami.backend.{Cotonoma, Node}
import cotoami.components.{material_symbol, optionalClasses, SplitPane}

object FormCoto {
  val StorageKeyPrefix = "FormCoto."

  case class Model(
      name: String,
      form: Form = CotoForm(),
      focused: Boolean = false,
      autoSave: Boolean = false
  ) {
    def folded: Boolean = !this.focused && this.isBlank

    def isBlank: Boolean =
      this.form match {
        case CotoForm(content)  => content.isBlank
        case CotonomaForm(name) => name.isBlank
      }

    def storageKey: String = StorageKeyPrefix + this.name

    def save: Cmd[Msg] =
      (autoSave, form) match {
        case (true, CotoForm(content)) =>
          Cmd(IO {
            dom.window.localStorage.setItem(this.storageKey, content)
            None
          })

        case _ => Cmd.none
      }

    def restore: Cmd[Msg] =
      (autoSave, form) match {
        case (true, form: CotoForm) =>
          restoreCotoContent.map(CotoContentRestored(_))

        case _ => Cmd.none
      }

    private def restoreCotoContent: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(this.storageKey)))
    })
  }

  def init(name: String, autoSave: Boolean): (Model, Cmd[Msg]) =
    Model(name, autoSave = autoSave) match {
      case model => (model, model.restore)
    }

  sealed trait Form
  case class CotoForm(content: String = "") extends Form
  case class CotonomaForm(name: String = "") extends Form

  sealed trait Msg
  case object SetCotoForm extends Msg
  case object SetCotonomaForm extends Msg
  case class CotoContentRestored(content: Option[String]) extends Msg
  case class CotoContentInput(content: String) extends Msg
  case class CotonomaNameInput(name: String) extends Msg
  case class SetFocus(focus: Boolean) extends Msg

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[Msg]]) =
    (msg, model.form) match {
      case (SetCotoForm, _) =>
        model.copy(form = CotoForm()) match {
          case model => (model, Seq(model.restore))
        }

      case (SetCotonomaForm, _) =>
        (model.copy(form = CotonomaForm()), Seq.empty)

      case (CotoContentRestored(Some(content)), form: CotoForm) =>
        (
          if (form.content.isBlank())
            model.copy(form = form.copy(content = content))
          else
            model,
          Seq()
        )

      case (CotoContentInput(content), form: CotoForm) =>
        model.copy(form = form.copy(content = content)) match {
          case model => (model, Seq(model.save))
        }

      case (CotonomaNameInput(name), form: CotonomaForm) =>
        (model.copy(form = form.copy(name = name)), Seq.empty)

      case (SetFocus(focus), _) =>
        (model.copy(focused = focus), Seq.empty)

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
          ("form-coto", true),
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
          className := "tool image default",
          data - "tooltip" := "Image",
          data - "placement" := "bottom",
          disabled := !model.form.isInstanceOf[CotoForm]
        )(
          material_symbol("image")
        ),
        button(
          className := "tool location default",
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
            resizable = !model.folded && !model.isBlank,
            className = None,
            onPrimarySizeChanged = onEditorHeightChanged
          )(
            SplitPane.Primary(className = Some("coto-editor"))(
              textarea(
                placeholder := "Write your Coto in Markdown here",
                value := content,
                onFocus := (_ => dispatch(SetFocus(true))),
                onBlur := (_ => dispatch(SetFocus(false))),
                onChange := (e => dispatch(CotoContentInput(e.target.value)))
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
              onFocus := (_ => dispatch(SetFocus(true))),
              onBlur := (_ => dispatch(SetFocus(false))),
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
