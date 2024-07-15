package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cats.effect.IO
import com.softwaremill.quicklens._

import fui._
import cotoami.Context
import cotoami.utils.{Log, Validation}
import cotoami.backend._
import cotoami.models.{WaitingPost, WaitingPosts}
import cotoami.components.{
  materialSymbol,
  optionalClasses,
  ScrollArea,
  SplitPane,
  ToolButton
}

object FormCoto {
  final val StorageKeyPrefix = "FormCoto."

  def init(id: String, autoSave: Boolean): (Model, Cmd[Msg]) =
    Model(id, autoSave = autoSave) match {
      case model => (model, model.restore)
    }

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      id: String,
      form: Form = CotoForm(),
      focused: Boolean = false,
      editorBeingResized: Boolean = false,
      imeActive: Boolean = false,
      autoSave: Boolean = false,
      inPreview: Boolean = false,
      posting: Boolean = false
  ) {
    def editorId: String = s"${this.id}-editor"

    def folded: Boolean =
      !this.focused && !this.editorBeingResized && this.isBlank

    def isBlank: Boolean =
      this.form match {
        case CotoForm(content)     => content.isBlank
        case CotonomaForm(name, _) => name.isBlank
      }

    def readyToPost: Boolean =
      !this.isBlank && !this.posting && (this.form match {
        case form: CotoForm              => form.validate.validated
        case CotonomaForm(_, validation) => validation.validated
      })

    def clear: Model =
      this.copy(
        form = this.form match {
          case form: CotoForm     => CotoForm()
          case form: CotonomaForm => CotonomaForm()
        },
        inPreview = false
      )

    def storageKey: String = StorageKeyPrefix + this.id

    def save: Cmd[Msg] =
      (this.autoSave, this.form) match {
        case (true, CotoForm(coto)) =>
          Cmd(IO {
            dom.window.localStorage.setItem(this.storageKey, coto)
            None
          })

        case _ => Cmd.none
      }

    def restore: Cmd[Msg] =
      (this.autoSave, this.form) match {
        case (true, form: CotoForm) =>
          restoreCoto.map(Msg.CotoRestored)

        case _ => Cmd.none
      }

    private def restoreCoto: Cmd[Option[String]] = Cmd(IO {
      Some(Option(dom.window.localStorage.getItem(this.storageKey)))
    })
  }

  /////////////////////////////////////////////////////////////////////////////
  // Form
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Form

  case class CotoForm(cotoInput: String = "") extends Form with CotoContent {
    override def summary: Option[String] =
      if (this.hasSummary)
        Some(this.firstLine.stripPrefix(CotoForm.SummaryPrefix).trim)
      else
        None

    override def content: Option[String] =
      if (this.hasSummary)
        Some(this.cotoInput.stripPrefix(this.firstLine).trim)
      else
        Some(this.cotoInput.trim)

    override def isCotonoma: Boolean = false

    def validate: Validation.Result =
      if (this.cotoInput.isBlank())
        Validation.Result.toBeValidated
      else {
        val errors =
          this.summary.map(Coto.validateSummary(_)).getOrElse(Seq.empty) ++
            Coto.validateContent(this.content.get) // this.content must be Some
        Validation.Result(errors)
      }

    private def hasSummary: Boolean =
      this.cotoInput.startsWith(CotoForm.SummaryPrefix)

    private def firstLine = this.cotoInput.linesIterator.next()
  }

  object CotoForm {
    // A top-level heading as the first line will be used as a summary.
    // cf. https://spec.commonmark.org/0.31.2/#atx-headings
    val SummaryPrefix = "# "
  }

  case class CotonomaForm(
      nameInput: String = "",
      validation: Validation.Result = Validation.Result.toBeValidated
  ) extends Form {
    def name: String = this.nameInput.trim

    def validate(nodeId: Id[Node]): (CotonomaForm, Seq[Cmd[Msg]]) = {
      val (validation, cmds) =
        if (this.name.isEmpty())
          (Validation.Result.toBeValidated, Seq.empty)
        else
          Cotonoma.validateName(this.name) match {
            case errors if errors.isEmpty =>
              (
                // Now that the local validation has passed,
                // wait for backend validation to be done.
                Validation.Result.toBeValidated,
                Seq(
                  Cotonoma.fetchByName(this.name, nodeId)
                    .map(Msg.CotonomaByName(this.name, _))
                )
              )
            case errors => (Validation.Result(errors), Seq.empty)
          }
      (this.copy(validation = validation), cmds)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg

  object Msg {
    case object SetCotoForm extends Msg
    case object SetCotonomaForm extends Msg
    case class CotoRestored(coto: Option[String]) extends Msg
    case class CotoInput(coto: String) extends Msg
    case class CotonomaNameInput(name: String) extends Msg
    case object ImeCompositionStart extends Msg
    case object ImeCompositionEnd extends Msg
    case class CotonomaByName(
        name: String,
        result: Either[ErrorJson, Cotonoma]
    ) extends Msg
    case class SetFocus(focus: Boolean) extends Msg
    case object EditorResizeStart extends Msg
    case object EditorResizeEnd extends Msg
    case object TogglePreview extends Msg
    case object Post extends Msg
    case class CotoPosted(postId: String, result: Either[ErrorJson, Coto])
        extends Msg
    case class CotonomaPosted(
        postId: String,
        result: Either[ErrorJson, js.Tuple2[CotonomaJson, CotoJson]]
    ) extends Msg
  }

  def update(
      msg: Msg,
      model: Model,
      waitingPosts: WaitingPosts
  )(implicit context: Context): (Model, WaitingPosts, Log, Seq[Cmd[Msg]]) = {
    val default = (model, waitingPosts, context.log, Seq.empty)
    (msg, model.form, context.domain.currentCotonoma) match {
      case (Msg.SetCotoForm, _, _) =>
        default.copy(
          _1 = model.copy(form = CotoForm()),
          _4 = Seq(model.restore)
        )

      case (Msg.SetCotonomaForm, _, _) =>
        default.copy(_1 = model.copy(form = CotonomaForm()))

      case (Msg.CotoRestored(Some(coto)), form: CotoForm, _) =>
        default.copy(
          _1 =
            if (form.cotoInput.isBlank())
              model.copy(form = form.copy(cotoInput = coto))
            else
              model,
          _3 = context.log.info("Coto draft restored")
        )

      case (Msg.CotoInput(coto), form: CotoForm, _) =>
        default.copy(
          _1 = model.copy(form = form.copy(cotoInput = coto)),
          _4 = Seq(model.save)
        )

      case (
            Msg.CotonomaNameInput(name),
            form: CotonomaForm,
            Some(cotonoma)
          ) => {
        val (newForm, cmds) =
          form.copy(nameInput = name).validate(cotonoma.nodeId)
        default.copy(
          _1 = model.copy(form = newForm),
          _4 =
            if (!model.imeActive)
              cmds
            else
              Seq.empty
        )
      }

      case (Msg.ImeCompositionStart, _, _) =>
        default.copy(_1 = model.copy(imeActive = true))

      case (Msg.ImeCompositionEnd, form, currentCotonoma) =>
        model.copy(imeActive = false) match {
          case model => {
            (form, currentCotonoma) match {
              case (form: CotonomaForm, Some(cotonoma)) => {
                val (newForm, cmds) = form.validate(cotonoma.nodeId)
                default.copy(
                  _1 = model.copy(form = newForm),
                  _4 = cmds
                )
              }
              case _ => default
            }
          }
        }

      case (
            Msg.CotonomaByName(name, Right(cotonoma)),
            form: CotonomaForm,
            _
          ) =>
        if (cotonoma.name == form.name)
          form.modify(_.validation).setTo(
            Validation.Result(
              Validation.Error(
                "cotonoma-already-exists",
                s"The cotonoma \"${cotonoma.name}\" already exists in this node.",
                Map("name" -> cotonoma.name, "id" -> cotonoma.id.uuid)
              )
            )
          ) match {
            case form => default.copy(_1 = model.copy(form = form))
          }
        else
          default

      case (Msg.CotonomaByName(name, Left(error)), form: CotonomaForm, _) =>
        if (name == form.name && error.code == "not-found")
          form.copy(validation = Validation.Result.validated) match {
            case form => default.copy(_1 = model.copy(form = form))
          }
        else
          default.copy(_3 =
            context.log.error(
              "CotonomaByName error.",
              Some(js.JSON.stringify(error))
            )
          )

      case (Msg.SetFocus(focus), _, _) =>
        default.copy(_1 = model.copy(focused = focus))

      case (Msg.EditorResizeStart, _, _) =>
        default.copy(_1 = model.copy(editorBeingResized = true))

      case (Msg.EditorResizeEnd, _, _) =>
        default.copy(
          _1 = model.copy(editorBeingResized = false),
          // Return the focus to the editor in order for it
          // not to be folded when it's empty.
          _4 = Seq(Cmd(IO {
            dom.document.getElementById(model.editorId) match {
              case element: HTMLElement => element.focus()
              case _                    => ()
            }
            None
          }))
        )

      case (Msg.TogglePreview, _, _) =>
        default.copy(_1 = model.modify(_.inPreview).using(!_))

      case (Msg.Post, form: CotoForm, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = waitingPosts.addCoto(postId, form, cotonoma),
              _4 = Seq(
                postCoto(postId, form, cotonoma.id),
                model.save
              )
            )
        }
      }

      case (Msg.Post, form: CotonomaForm, Some(cotonoma)) => {
        val postId = WaitingPost.newPostId()
        model.copy(posting = true).clear match {
          case model =>
            default.copy(
              _1 = model,
              _2 = waitingPosts.addCotonoma(postId, form, cotonoma),
              _4 = Seq(postCotonoma(postId, form, cotonoma.id))
            )
        }
      }

      case (Msg.CotoPosted(postId, Right(coto)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.remove(postId),
          _3 = context.log.info("Coto posted.", Some(coto.id.uuid))
        )

      case (Msg.CotoPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.setError(
            postId,
            s"Couldn't post this coto: ${error}"
          ),
          _3 = context.log.error("Couldn't post a coto.", Some(error))
        )
      }

      case (Msg.CotonomaPosted(postId, Right(cotonoma)), _, _) =>
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.remove(postId),
          _3 = context.log.info(
            "Cotonoma posted.",
            Some(js.JSON.stringify(cotonoma._1))
          )
        )

      case (Msg.CotonomaPosted(postId, Left(e)), _, _) => {
        val error = js.JSON.stringify(e)
        default.copy(
          _1 = model.copy(posting = false),
          _2 = waitingPosts.setError(
            postId,
            s"Couldn't post this cotonoma: ${error}"
          ),
          _3 = context.log.error("Couldn't post a cotonoma.", Some(error))
        )
      }

      case (_, _, _) => default
    }
  }

  private def postCoto(
      postId: String,
      form: CotoForm,
      postTo: Id[Cotonoma]
  ): Cmd[Msg] =
    Coto.post(form.content.getOrElse(""), form.summary, postTo)
      .map(Msg.CotoPosted(postId, _))

  private def postCotonoma(
      postId: String,
      form: CotonomaForm,
      post_to: Id[Cotonoma]
  ): Cmd[Msg] =
    Commands
      .send(Commands.PostCotonoma(form.name, post_to))
      .map(Msg.CotonomaPosted(postId, _))

  /////////////////////////////////////////////////////////////////////////////
  // view
  /////////////////////////////////////////////////////////////////////////////

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
      headerTools(model, dispatch),
      model.form match {
        case form: CotoForm =>
          SplitPane(
            vertical = false,
            initialPrimarySize = editorHeight,
            resizable = !model.folded,
            className = None,
            onResizeStart = Some(() => dispatch(Msg.EditorResizeStart)),
            onResizeEnd = Some(() => dispatch(Msg.EditorResizeEnd)),
            onPrimarySizeChanged = Some(onEditorHeightChanged)
          )(
            if (model.inPreview)
              SplitPane.Primary(
                className = Some("coto-preview"),
                onClick = None
              )(
                ScrollArea(
                  scrollableElementId = None,
                  autoHide = true,
                  bottomThreshold = None,
                  onScrollToBottom = () => ()
                )(
                  section(className := "coto-preview")(
                    form.summary.map(section(className := "summary")(_)),
                    div(className := "content")(
                      ViewCoto.sectionCotoContentDetails(form)
                    )
                  )
                )
              )
            else
              SplitPane.Primary(
                className = Some("coto-editor"),
                onClick = None
              )(
                textarea(
                  id := model.editorId,
                  placeholder := "Write your Coto in Markdown",
                  value := form.cotoInput,
                  onFocus := (_ => dispatch(Msg.SetFocus(true))),
                  onBlur := (_ => dispatch(Msg.SetFocus(false))),
                  onChange := (e => dispatch(Msg.CotoInput(e.target.value))),
                  onCompositionStart := (_ =>
                    dispatch(Msg.ImeCompositionStart)
                  ),
                  onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
                  onKeyDown := (e =>
                    if (model.readyToPost && detectCtrlEnter(e)) {
                      dispatch(Msg.Post)
                    }
                  )
                )
              ),
            SplitPane.Secondary(className = None, onClick = None)(
              divPost(
                model,
                form.validate,
                operatingNode,
                currentCotonoma,
                dispatch
              )
            )
          )

        case CotonomaForm(cotonomaName, validation) =>
          Fragment(
            input(
              `type` := "text",
              name := "cotonomaName",
              placeholder := "New cotonoma name",
              value := cotonomaName,
              Validation.ariaInvalid(validation),
              onFocus := (_ => dispatch(Msg.SetFocus(true))),
              onBlur := (_ => dispatch(Msg.SetFocus(false))),
              onChange := (e =>
                dispatch(Msg.CotonomaNameInput(e.target.value))
              ),
              onCompositionStart := (_ => dispatch(Msg.ImeCompositionStart)),
              onCompositionEnd := (_ => dispatch(Msg.ImeCompositionEnd)),
              onKeyDown := (e =>
                if (model.readyToPost && detectCtrlEnter(e)) {
                  dispatch(Msg.Post)
                }
              )
            ),
            divPost(model, validation, operatingNode, currentCotonoma, dispatch)
          )
      }
    )

  private def headerTools(model: Model, dispatch: Msg => Unit): ReactElement =
    header(className := "tools")(
      section(className := "coto-type-switch")(
        button(
          className := "new-coto default",
          disabled := model.form.isInstanceOf[CotoForm],
          onClick := (_ => dispatch(Msg.SetCotoForm))
        )(
          span(className := "label")(
            materialSymbol("text_snippet"),
            "Coto"
          )
        ),
        button(
          className := "new-cotonoma default",
          disabled := model.form.isInstanceOf[CotonomaForm],
          onClick := (_ => dispatch(Msg.SetCotonomaForm))
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
    )

  private def divPost(
      model: Model,
      validation: Validation.Result,
      operatingNode: Node,
      currentCotonoma: Cotonoma,
      dispatch: Msg => Unit
  ): ReactElement =
    div(className := "post")(
      Validation.sectionValidationError(validation),
      section(className := "post")(
        address(className := "poster")(
          spanNode(operatingNode)
        ),
        div(className := "buttons")(
          Option.when(model.form.isInstanceOf[CotoForm]) {
            button(
              className := "preview contrast outline",
              disabled := !model.readyToPost,
              onClick := (_ => dispatch(Msg.TogglePreview))
            )(
              if (model.inPreview)
                "Edit"
              else
                "Preview"
            )
          },
          button(
            className := "post",
            disabled := !model.readyToPost,
            aria - "busy" := model.posting.toString(),
            onClick := (_ => dispatch(Msg.Post))
          )(
            "Post to ",
            span(className := "target-cotonoma")(
              currentCotonoma.abbreviateName(15)
            ),
            span(className := "shortcut-help")("(Ctrl + Enter)")
          )
        )
      )
    )
}
