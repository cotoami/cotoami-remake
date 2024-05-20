package cotoami.subparts

import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.ReactElement
import slinky.web.html._

import cats.effect.IO

import fui.FunctionalUI._
import cotoami.backend.{Cotonoma, Node}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  SplitPane,
  ToolButton
}

object FormCoto {
  val StorageKeyPrefix = "FormCoto."

  case class Model(
      id: String,
      form: Form = CotoForm(),
      focused: Boolean = false,
      editorBeingResized: Boolean = false,
      autoSave: Boolean = false
  ) {
    def editorId: String = s"${this.id}-editor"

    def folded: Boolean =
      !this.focused && !this.editorBeingResized && this.isBlank

    def isBlank: Boolean =
      this.form match {
        case CotoForm(content)  => content.isBlank
        case CotonomaForm(name) => name.isBlank
      }

    def storageKey: String = StorageKeyPrefix + this.id

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
          restoreCotoContent.map(CotoContentRestored)

        case _ => Cmd.none
      }

    private def restoreCotoContent: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(this.storageKey)))
    })
  }

  def init(id: String, autoSave: Boolean): (Model, Cmd[Msg]) =
    Model(id, autoSave = autoSave) match {
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
  case object EditorResizeStart extends Msg
  case object EditorResizeEnd extends Msg

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

      case (EditorResizeStart, _) =>
        (model.copy(editorBeingResized = true), Seq.empty)

      case (EditorResizeEnd, _) =>
        (
          model.copy(editorBeingResized = false),
          // Return the focus to the editor in order for it
          // not to be folded when it's empty.
          Seq(Cmd(IO {
            dom.document.getElementById(model.editorId) match {
              case element: HTMLElement => element.focus()
              case _                    => ()
            }
            None
          }))
        )

      case (_, _) => (model, Seq.empty)
    }

  def apply(
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
              materialSymbol("text_snippet"),
              "Coto"
            )
          ),
          button(
            className := "new-cotonoma default",
            disabled := model.form.isInstanceOf[CotonomaForm],
            onClick := (_ => dispatch(SetCotonomaForm))
          )(
            span(className := "label")(
              materialSymbol("topic"),
              "Cotonoma"
            )
          )
        ),
        ToolButton(
          classes = "image",
          tip = "Image",
          disabled = !model.form.isInstanceOf[CotoForm],
          symbol = "image"
        ),
        ToolButton(
          classes = "location",
          tip = "Location",
          symbol = "location_on"
        )
      ),
      model.form match {
        case CotoForm(content) =>
          SplitPane(
            vertical = false,
            initialPrimarySize = editorHeight,
            resizable = !model.folded,
            className = None,
            onResizeStart = Some(() => dispatch(EditorResizeStart)),
            onResizeEnd = Some(() => dispatch(EditorResizeEnd)),
            onPrimarySizeChanged = Some(onEditorHeightChanged)
          )(
            SplitPane.Primary(className = Some("coto-editor"), onClick = None)(
              textarea(
                id := model.editorId,
                placeholder := "Write your Coto in Markdown here",
                value := content,
                onFocus := (_ => dispatch(SetFocus(true))),
                onBlur := (_ => dispatch(SetFocus(false))),
                onChange := (e => dispatch(CotoContentInput(e.target.value)))
              )
            ),
            SplitPane.Secondary(className = None, onClick = None)(
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

  private def inputFooter(
      model: Model,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    footer(className := "post")(
      address(className := "poster")(
        nodeImg(operatingNode),
        operatingNode.name
      ),
      button(className := "post", disabled := model.isBlank)(
        "Post to ",
        span(className := "target-cotonoma")(
          currentCotonoma.abbreviateName(15)
        ),
        span(className := "shortcut-help")("(Ctrl + Enter)")
      )
    )
}
